package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.NetWorth
import com.beancounter.common.contracts.NetWorthClassification
import com.beancounter.common.contracts.NetWorthPortfolio
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.position.composite.AssetConfigClient
import com.beancounter.position.composite.PrivateAssetConfigDto
import com.beancounter.position.valuation.Valuation
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Server-side Net Worth headline — the derivation bc-view's
 * useWealthSummary / useNetWorthData used to run in the browser.
 *
 * - holdingsValue: live aggregated positions total, PORTFOLIO view, which the
 *   aggregator already prices in the target currency.
 * - standaloneCompositeValue: non-reserve sub-account balances of composite
 *   configs (CPF / pensions) whose parent asset is NOT held in any selected
 *   portfolio. Held parents already carry the balance via CompositeValuation,
 *   so adding them again would double count.
 * - healthcareReserve: every MA sub-account balance, reported but not added.
 * - FX: one request per call carrying every distinct (source → target) pair;
 *   a missing rate fails fast rather than silently applying 1.0.
 */
@Service
class NetWorthService(
    private val valuationService: Valuation,
    private val assetConfigClient: AssetConfigClient,
    private val fxService: FxService,
    private val tokenService: TokenService
) {
    fun calculate(
        portfolios: Collection<Portfolio>,
        asAt: String,
        currency: String
    ): NetWorth {
        val target = currency.trim().uppercase()
        val positions =
            if (portfolios.isEmpty()) {
                Positions()
            } else {
                valuationService.getAggregatedPositions(portfolios, asAt, true, target).data
            }
        val heldAssetIds =
            positions.positions.values
                .map { it.asset.id }
                .toSet()
        val composite = compositeTotals(assetConfigClient.findAll(), heldAssetIds)

        val sourceCurrencies =
            buildSet {
                positions.totals[Position.In.PORTFOLIO]?.let { add(it.currency.code) }
                portfolios.forEach { add(it.base.code) }
                addAll(composite.standalone.keys)
                addAll(composite.reserve.keys)
            }
        val rates = rates(sourceCurrencies, target, asAt)

        val holdingsTotals = positions.totals[Position.In.PORTFOLIO]
        val holdingsValue =
            if (holdingsTotals == null) {
                BigDecimal.ZERO
            } else {
                holdingsTotals.marketValue.multiply(rates.getValue(holdingsTotals.currency.code))
            }
        val standaloneCompositeValue = money(convert(composite.standalone, rates))
        val healthcareReserve = money(convert(composite.reserve, rates))
        // Sum the rounded components so totalValue always reconciles with what is returned.
        val totalValue = money(holdingsValue).add(standaloneCompositeValue)

        return NetWorth(
            asAt = asAt,
            currency = target,
            totalValue = totalValue,
            holdingsValue = money(holdingsValue),
            standaloneCompositeValue = standaloneCompositeValue,
            healthcareReserve = healthcareReserve,
            gainOnDay = money(gainOnDay(positions)),
            portfolioCount = portfolios.size,
            classificationBreakdown = classificationBreakdown(positions),
            portfolios = portfolioRows(portfolios, rates, totalValue)
        )
    }

    private class CompositeTotals(
        val standalone: Map<String, BigDecimal>,
        val reserve: Map<String, BigDecimal>
    )

    private fun compositeTotals(
        configs: List<PrivateAssetConfigDto>,
        heldAssetIds: Set<String>
    ): CompositeTotals {
        val standalone = mutableMapOf<String, BigDecimal>()
        val reserve = mutableMapOf<String, BigDecimal>()
        for (config in configs) {
            if (config.subAccounts.isEmpty()) continue
            val currency =
                config.rentalCurrency
                    .orEmpty()
                    .ifBlank { DEFAULT_CONFIG_CURRENCY }
                    .uppercase()
            val (reserveBalance, nonReserveBalance) =
                config.subAccounts
                    .filter { it.balance.signum() != 0 }
                    .partition { it.code == HEALTHCARE_RESERVE_CODE }
                    .let { (ma, other) -> sum(ma.map { it.balance }) to sum(other.map { it.balance }) }
            if (reserveBalance.signum() > 0) {
                reserve.merge(currency, reserveBalance, BigDecimal::add)
            }
            if (nonReserveBalance.signum() > 0 && config.assetId !in heldAssetIds) {
                standalone.merge(currency, nonReserveBalance, BigDecimal::add)
            }
        }
        return CompositeTotals(standalone, reserve)
    }

    /**
     * Spot rate from each source currency into [target], keyed by source
     * code. Same-currency entries are 1; a pair svc-data cannot price is an
     * error, never a silent 1.0.
     */
    private fun rates(
        sourceCurrencies: Set<String>,
        target: String,
        asAt: String
    ): Map<String, BigDecimal> {
        val pairs = sourceCurrencies.filter { it != target }.map { IsoCurrencyPair(it, target) }
        if (pairs.isEmpty()) {
            return sourceCurrencies.associateWith { BigDecimal.ONE }
        }
        val fxRequest = FxRequest(rateDate = asAt)
        pairs.forEach { fxRequest.add(it) }
        val fxRates = fxService.getRates(fxRequest, tokenService.bearerToken).data.rates
        return sourceCurrencies.associateWith { source ->
            if (source == target) {
                BigDecimal.ONE
            } else {
                val pair = IsoCurrencyPair(source, target)
                fxRates[pair]?.rate ?: throw BusinessException("No FX rate for $pair as at $asAt")
            }
        }
    }

    private fun convert(
        byCurrency: Map<String, BigDecimal>,
        rates: Map<String, BigDecimal>
    ): BigDecimal =
        byCurrency.entries.fold(BigDecimal.ZERO) { acc, (currency, amount) ->
            acc.add(amount.multiply(rates.getValue(currency)))
        }

    /**
     * gainOnDay is meaningless without a price move, so only positions whose
     * PORTFOLIO price data carries a non-zero change contribute.
     */
    private fun gainOnDay(positions: Positions): BigDecimal =
        positions.positions.values
            .mapNotNull { it.moneyValues[Position.In.PORTFOLIO] }
            .filter { it.priceData.changePercent.signum() != 0 }
            .fold(BigDecimal.ZERO) { acc, mv -> acc.add(mv.gainOnDay) }

    private fun classificationBreakdown(positions: Positions): List<NetWorthClassification> {
        val byGroup = mutableMapOf<String, BigDecimal>()
        for (position in positions.positions.values) {
            val marketValue = position.moneyValues[Position.In.PORTFOLIO]?.marketValue ?: continue
            byGroup.merge(liquidityGroup(position.asset.effectiveReportCategory), marketValue, BigDecimal::add)
        }
        val total = sum(byGroup.values)
        return byGroup.entries
            .sortedByDescending { it.value }
            .map { (group, value) ->
                NetWorthClassification(
                    classification = group,
                    value = money(value),
                    percentage = percentage(value, total)
                )
            }
    }

    private fun portfolioRows(
        portfolios: Collection<Portfolio>,
        rates: Map<String, BigDecimal>,
        totalValue: BigDecimal
    ): List<NetWorthPortfolio> =
        portfolios.map { portfolio ->
            val value = portfolio.marketValue.multiply(rates.getValue(portfolio.base.code))
            NetWorthPortfolio(
                id = portfolio.id,
                code = portfolio.code,
                name = portfolio.name,
                value = money(value),
                percentage = percentage(value, totalValue),
                irr = portfolio.irr
            )
        }

    private fun percentage(
        value: BigDecimal,
        total: BigDecimal
    ): BigDecimal =
        if (total.signum() == 0) {
            BigDecimal.ZERO.setScale(MONEY_SCALE)
        } else {
            value.multiply(ONE_HUNDRED).divide(total, MONEY_SCALE, RoundingMode.HALF_UP)
        }

    private fun sum(values: Collection<BigDecimal>): BigDecimal = values.fold(BigDecimal.ZERO, BigDecimal::add)

    private fun money(value: BigDecimal): BigDecimal = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    companion object {
        private const val MONEY_SCALE = 2
        private const val DEFAULT_CONFIG_CURRENCY = "USD"
        private const val HEALTHCARE_RESERVE_CODE = "MA"
        private val ONE_HUNDRED = BigDecimal(100)

        const val GROUP_INVESTMENT = "Investment"
        const val GROUP_CASH = "Cash"
        const val GROUP_PROPERTY = "Property"
        const val GROUP_RETIREMENT = "Retirement"
        const val GROUP_OTHER = "Other"

        /**
         * Liquidity group for a report category. Mirrors bc-view's
         * mapToLiquidityGroup. Keyed only on the [AssetCategory.REPORT_*]
         * constants: svc-data never writes `Asset.reportCategory`, so
         * `effectiveReportCategory` always comes through
         * [AssetCategory.toReportCategory], which normalises every configured
         * category id to one of these constants.
         */
        fun liquidityGroup(category: String): String =
            when (category) {
                AssetCategory.REPORT_EQUITY,
                AssetCategory.REPORT_ETF,
                AssetCategory.REPORT_MUTUAL_FUND,
                AssetCategory.REPORT_INDEX -> GROUP_INVESTMENT
                AssetCategory.REPORT_CASH -> GROUP_CASH
                AssetCategory.REPORT_PROPERTY -> GROUP_PROPERTY
                AssetCategory.REPORT_RETIREMENT_FUND -> GROUP_RETIREMENT
                else -> GROUP_OTHER
            }
    }
}