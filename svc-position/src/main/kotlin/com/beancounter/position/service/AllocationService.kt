package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.AllocationData
import com.beancounter.common.contracts.CategoryAllocation
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.position.composite.AssetConfigClient
import com.beancounter.position.composite.PrivateAssetConfigDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Service for calculating asset allocation from portfolio positions.
 * Groups positions by report category and calculates allocation percentages.
 */
@Service
class AllocationService(
    private val tokenService: TokenService,
    private val fxService: FxService,
    private val assetConfigClient: AssetConfigClient
) {
    /**
     * Calculate allocation breakdown from positions.
     *
     * @param positions The valued positions to analyze
     * @param valueIn Which currency context to use (BASE, PORTFOLIO, or TRADE)
     * @return AllocationData with category breakdown and aggregated allocations
     */
    fun calculateAllocation(
        positions: Positions,
        valueIn: Position.In = Position.In.PORTFOLIO
    ): AllocationData {
        if (!positions.hasPositions()) {
            return AllocationData()
        }

        val totals = positions.totals[valueIn]
        val totalValue = totals?.marketValue ?: BigDecimal.ZERO
        val currency = totals?.currency?.code ?: positions.portfolio.currency.code

        if (totalValue == BigDecimal.ZERO) {
            return AllocationData(currency = currency)
        }

        // Group by report category
        val categoryValues = mutableMapOf<String, BigDecimal>()

        for (position in positions.positions.values) {
            val marketValue = position.moneyValues[valueIn]?.marketValue ?: BigDecimal.ZERO
            if (marketValue > BigDecimal.ZERO) {
                val category = position.asset.effectiveReportCategory
                categoryValues[category] = (categoryValues[category] ?: BigDecimal.ZERO) + marketValue
            }
        }

        // Calculate percentages for each category
        val categoryBreakdown =
            categoryValues
                .map { (category, value) ->
                    val percentage =
                        value
                            .divide(totalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal(100))
                            .setScale(2, RoundingMode.HALF_UP)
                    category to
                        CategoryAllocation(
                            category = category,
                            marketValue = value.setScale(2, RoundingMode.HALF_UP),
                            percentage = percentage
                        )
                }.toMap()

        // Aggregate into retirement plan categories
        val cashAllocation = sumCategoryPercentage(categoryBreakdown, CASH_CATEGORIES)
        val equityAllocation = sumCategoryPercentage(categoryBreakdown, EQUITY_CATEGORIES)
        val housingAllocation = sumCategoryPercentage(categoryBreakdown, HOUSING_CATEGORIES)

        val heldAssetIds =
            positions.positions.values
                .map { it.asset.id }
                .toSet()

        val (compositeLiquid, compositeNonLiquid) = compositeSplit(positions, valueIn)

        return AllocationData(
            cashAllocation = cashAllocation,
            equityAllocation = equityAllocation,
            housingAllocation = housingAllocation,
            totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
            currency = currency,
            categoryBreakdown = categoryBreakdown,
            heldAssetIds = heldAssetIds,
            compositeLiquid = compositeLiquid.setScale(2, RoundingMode.HALF_UP),
            compositeNonLiquid = compositeNonLiquid.setScale(2, RoundingMode.HALF_UP)
        )
    }

    /**
     * Splits the value of any composite-policy position (currently CPF —
     * any position whose [Position.subAccounts] map is non-empty) into a
     * liquid and non-liquid portion, in the requested valuation bucket's
     * currency. The split is driven by the sub-account [liquid] flag on
     * [com.beancounter.position.composite.SubAccountDto] — the same flag
     * jar-common's [com.beancounter.common.composite.CompositeValuation]
     * uses, so svc-position and svc-retire stay in lock-step.
     *
     * The position's bucket [MoneyValues.marketValue] is the truth for
     * what the user sees; we apply the liquid/total ratio (derived from
     * the asset config sub-account balances) to that figure. That keeps
     * the FX conversion path identical to the rest of the allocation
     * math — there is no second currency hop.
     */
    private fun compositeSplit(
        positions: Positions,
        valueIn: Position.In
    ): Pair<BigDecimal, BigDecimal> {
        var liquid = BigDecimal.ZERO
        var nonLiquid = BigDecimal.ZERO
        for (position in positions.positions.values) {
            if (position.subAccounts.isEmpty()) continue
            val marketValue = position.moneyValues[valueIn]?.marketValue ?: BigDecimal.ZERO
            if (marketValue.signum() <= 0) continue
            val config =
                try {
                    assetConfigClient.find(position.asset.id)
                } catch (_: NotFoundException) {
                    // Position has sub-accounts but no config — degrade
                    // gracefully (treat all as liquid via the default
                    // bucket logic upstream).
                    continue
                } catch (ex: BusinessException) {
                    log.warn(
                        "Composite split skipped for {}: {}",
                        position.asset.id,
                        ex.message
                    )
                    continue
                }
            val (liquidShare, nonLiquidShare) = splitMarketValue(config, marketValue)
            liquid = liquid.add(liquidShare)
            nonLiquid = nonLiquid.add(nonLiquidShare)
        }
        return liquid to nonLiquid
    }

    private fun splitMarketValue(
        config: PrivateAssetConfigDto,
        marketValue: BigDecimal
    ): Pair<BigDecimal, BigDecimal> {
        val total =
            config.subAccounts
                .fold(BigDecimal.ZERO) { acc, sa -> acc.add(sa.balance) }
        if (total.signum() <= 0) return BigDecimal.ZERO to BigDecimal.ZERO
        val liquidBalance =
            config.subAccounts
                .filter { it.liquid }
                .fold(BigDecimal.ZERO) { acc, sa -> acc.add(sa.balance) }
        val liquidShare =
            marketValue
                .multiply(liquidBalance)
                .divide(total, 4, RoundingMode.HALF_UP)
        val nonLiquidShare = marketValue.subtract(liquidShare)
        return liquidShare to nonLiquidShare
    }

    private fun sumCategoryPercentage(
        breakdown: Map<String, CategoryAllocation>,
        categories: Set<String>
    ): BigDecimal =
        categories
            .mapNotNull { breakdown[it]?.percentage }
            .fold(BigDecimal.ZERO) { acc, pct -> acc + pct }
            .setScale(2, RoundingMode.HALF_UP)

    /**
     * Convert allocation values to a target currency.
     *
     * @param allocation The source allocation data
     * @param targetCurrency The target currency code (e.g., "NZD")
     * @param fxService The FX service to fetch rates
     * @param rateDate The date for FX rate lookup (defaults to today)
     * @return AllocationData with values converted to target currency
     */
    fun convertCurrency(
        allocation: AllocationData,
        targetCurrency: String,
        fxService: FxService,
        rateDate: String = "today"
    ): AllocationData {
        if (allocation.totalValue == BigDecimal.ZERO) {
            return allocation.copy(currency = targetCurrency)
        }

        val sourceCurrency = allocation.currency
        val pair = IsoCurrencyPair(from = sourceCurrency, to = targetCurrency)

        val fxRequest = FxRequest(rateDate = rateDate).add(pair)
        val fxResponse = fxService.getRates(fxRequest, tokenService.bearerToken)
        val rate = fxResponse.data.rates[pair]?.rate ?: BigDecimal.ONE

        log.debug(
            "Converting allocation from {} to {}: rate={}",
            sourceCurrency,
            targetCurrency,
            rate
        )

        // Convert total value
        val convertedTotalValue = allocation.totalValue.multiply(rate).setScale(2, RoundingMode.HALF_UP)

        // Convert category market values (percentages stay the same)
        val convertedBreakdown =
            allocation.categoryBreakdown.mapValues { (_, categoryAllocation) ->
                categoryAllocation.copy(
                    marketValue =
                        categoryAllocation.marketValue
                            .multiply(rate)
                            .setScale(2, RoundingMode.HALF_UP)
                )
            }

        return allocation.copy(
            totalValue = convertedTotalValue,
            currency = targetCurrency,
            categoryBreakdown = convertedBreakdown
        )
    }

    /**
     * Convert aggregated positions' PORTFOLIO view to a target currency.
     *
     * Aggregated positions share a single context portfolio currency (see
     * ValuationService.getAggregatedPositions), so one FX rate is applied
     * across every position's Position.In.PORTFOLIO money values and to the
     * PORTFOLIO totals. TRADE and BASE views are untouched.
     *
     * @param positions aggregated positions to convert
     * @param targetCurrencyCode target ISO currency code (e.g., "SGD")
     * @param rateDate FX rate lookup date
     * @return the same Positions instance with PORTFOLIO view in target currency
     */
    fun convertPositionsToCurrency(
        positions: Positions,
        targetCurrencyCode: String,
        rateDate: String = "today"
    ): Positions {
        if (!positions.hasPositions()) return positions

        val currentTotals = positions.totals[Position.In.PORTFOLIO] ?: return positions
        val sourceCurrency = currentTotals.currency
        if (sourceCurrency.code.equals(targetCurrencyCode, ignoreCase = true)) {
            return positions
        }

        val pair = IsoCurrencyPair(from = sourceCurrency.code, to = targetCurrencyCode.uppercase())
        val fxRequest = FxRequest(rateDate = rateDate).add(pair)
        val fxResponse = fxService.getRates(fxRequest, tokenService.bearerToken)
        val rate = fxResponse.data.rates[pair]?.rate
        if (rate == null) {
            log.warn(
                "FX rate not found for {}:{}; returning positions unchanged",
                sourceCurrency.code,
                targetCurrencyCode
            )
            return positions
        }

        val targetCurrency = Currency(targetCurrencyCode.uppercase())

        for (position in positions.positions.values) {
            val original = position.moneyValues[Position.In.PORTFOLIO] ?: continue
            position.moneyValues[Position.In.PORTFOLIO] =
                convertMoneyValues(original, targetCurrency, rate)
        }

        positions.totals[Position.In.PORTFOLIO] =
            currentTotals.copy(
                currency = targetCurrency,
                marketValue = currentTotals.marketValue.multiply(rate),
                purchases = currentTotals.purchases.multiply(rate),
                sales = currentTotals.sales.multiply(rate),
                cash = currentTotals.cash.multiply(rate),
                income = currentTotals.income.multiply(rate),
                gain = currentTotals.gain.multiply(rate)
                // irr is a percentage, not converted
            )

        return positions
    }

    private fun convertMoneyValues(
        original: MoneyValues,
        targetCurrency: Currency,
        rate: BigDecimal
    ): MoneyValues {
        val converted = MoneyValues(targetCurrency)
        converted.dividends = original.dividends.multiply(rate)
        converted.expenses = original.expenses.multiply(rate)
        converted.costValue = original.costValue.multiply(rate)
        converted.costBasis = original.costBasis.multiply(rate)
        converted.fees = original.fees.multiply(rate)
        converted.purchases = original.purchases.multiply(rate)
        converted.sales = original.sales.multiply(rate)
        converted.marketValue = original.marketValue.multiply(rate)
        converted.averageCost = original.averageCost.multiply(rate)
        converted.realisedGain = original.realisedGain.multiply(rate)
        converted.unrealisedGain = original.unrealisedGain.multiply(rate)
        converted.totalGain = original.totalGain.multiply(rate)
        converted.gainOnDay = original.gainOnDay.multiply(rate)
        // Ratios/percentages stay put; priceData describes the asset, not an amount.
        converted.weight = original.weight
        converted.irr = original.irr
        converted.roi = original.roi
        converted.priceData = original.priceData
        return converted
    }

    companion object {
        private val log = LoggerFactory.getLogger(AllocationService::class.java)

        // Categories that map to Cash allocation
        val CASH_CATEGORIES =
            setOf(
                AssetCategory.REPORT_CASH
            )

        // Categories that map to Equity allocation
        val EQUITY_CATEGORIES =
            setOf(
                AssetCategory.REPORT_EQUITY,
                AssetCategory.REPORT_ETF,
                AssetCategory.REPORT_MUTUAL_FUND
            )

        // Categories that map to Housing/Property allocation
        val HOUSING_CATEGORIES =
            setOf(
                AssetCategory.REPORT_PROPERTY
            )
    }
}