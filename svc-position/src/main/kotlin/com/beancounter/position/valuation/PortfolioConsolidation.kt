package com.beancounter.position.valuation

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioBreakdown
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.MathUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Sums independently accumulated per-portfolio positions into one set of positions
 * that reports as the request's context portfolio.
 *
 * Each portfolio is accumulated on its own first. Merging every portfolio's trns into
 * one stream and re-accumulating it double-applied any corporate action recorded per
 * portfolio: a 4:1 split written into each holding portfolio compounded across the
 * combined balance, and a SELL in one portfolio drew down a pooled average cost that
 * included another portfolio's lot.
 */
@Component
class PortfolioConsolidation(
    private val fxRateService: FxService,
    private val tokenService: TokenService
) {
    private val log = LoggerFactory.getLogger(PortfolioConsolidation::class.java)
    private val averageCost = AverageCost()

    /**
     * [perPortfolio] summed as [request]'s context portfolio, each aggregated position
     * carrying the quantity every contributing portfolio holds.
     */
    fun consolidate(
        request: AggregateValuationRequest,
        perPortfolio: List<Pair<Portfolio, Positions>>
    ): Positions {
        val contextPortfolio = request.contextPortfolio()
        // The cost buckets are accumulated in each source portfolio's own
        // currency. When the aggregate reports in a different currency (or
        // spans portfolios with differing currencies) those costs must be
        // FX-converted before summing, otherwise a raw foreign figure sits
        // under the reporting-currency label. Market value is (re)priced
        // later against the target rate; this keeps cost consistent with it.
        val sourcePositions = perPortfolio.map { it.second }
        val rates = aggregationRates(contextPortfolio, sourcePositions, request.valuationDate)
        val aggregated = mergePositions(contextPortfolio, sourcePositions, rates)
        applyPortfolioBreakdown(aggregated, perPortfolio)
        return aggregated
    }

    /**
     * Sum independently-accumulated per-portfolio positions into a single
     * aggregate keyed by asset. Quantities, cost and cash-flow history add
     * directly; TRADE and BASE buckets share a currency across a user's
     * portfolios so they sum exactly. The PORTFOLIO bucket adopts the
     * context (reporting) currency — exact when the portfolios share it.
     * Market value, gains and IRR are (re)derived later by valuation.
     */
    private fun mergePositions(
        contextPortfolio: Portfolio,
        perPortfolio: List<Positions>,
        rates: Map<IsoCurrencyPair, FxRate>
    ): Positions {
        val aggregated = Positions(contextPortfolio)
        perPortfolio.forEach { positions ->
            positions.positions.values.forEach { source ->
                val target = aggregated.getOrCreate(source.asset)
                target.addQuantities(source)
                source.moneyValues.forEach { (bucket, sourceMv) ->
                    val targetCurrency = targetBucketCurrency(bucket, sourceMv.currency, contextPortfolio)
                    target
                        .getMoneyValues(bucket, targetCurrency)
                        .addConverted(sourceMv, conversionRate(sourceMv.currency, targetCurrency, rates))
                }
                target.periodicCashFlows.addAll(source.periodicCashFlows.cashFlows)
                // Per-broker holdings survive aggregation so the UI can cap
                // a sell at what the chosen broker actually holds.
                source.held.forEach { (broker, qty) ->
                    target.held.merge(broker, qty, BigDecimal::add)
                }
                source.subAccounts.forEach { (code, balance) ->
                    target.subAccounts.merge(code, balance, BigDecimal::add)
                }
                target.adoptEarliestDates(source)
            }
        }
        // Restate unit cost from the summed basis so display stays consistent.
        aggregated.positions.values.forEach { position ->
            val total = position.quantityValues.getTotal()
            position.moneyValues.values.forEach { mv ->
                mv.averageCost = averageCost.value(mv.costBasis, total)
            }
        }
        return aggregated
    }

    /**
     * The currency each aggregated bucket is denominated in. TRADE stays in
     * the asset's own trade currency (shared across a user's portfolios);
     * PORTFOLIO adopts the context (reporting) currency and BASE the context
     * base, so summed cost lands in the same currency as the (re)priced
     * market value.
     */
    private fun targetBucketCurrency(
        bucket: Position.In,
        sourceCurrency: Currency,
        context: Portfolio
    ): Currency =
        when (bucket) {
            Position.In.TRADE -> sourceCurrency
            Position.In.PORTFOLIO -> context.currency
            Position.In.BASE -> context.base
        }

    /**
     * Fetch the spot FX rates needed to convert each source bucket's cost
     * into its aggregated (reporting) currency. Same-currency buckets need
     * no rate; when every bucket already matches the context currency the
     * request is skipped entirely (the common single-currency case).
     *
     * Cost basis is accumulated at many historical rates, so a single spot
     * rate is an approximation — mirroring how market value is priced and
     * how the frontend flags CUSTOM-currency cost as approximate.
     */
    private fun aggregationRates(
        contextPortfolio: Portfolio,
        perPortfolio: List<Positions>,
        valuationDate: String
    ): Map<IsoCurrencyPair, FxRate> {
        val pairs =
            perPortfolio
                .flatMap { it.positions.values }
                .flatMap { position ->
                    position.moneyValues.mapNotNull { (bucket, mv) ->
                        IsoCurrencyPair.toPair(mv.currency, targetBucketCurrency(bucket, mv.currency, contextPortfolio))
                    }
                }.toSet()
        if (pairs.isEmpty()) return emptyMap()

        val fxRequest = FxRequest(rateDate = valuationDate)
        pairs.forEach { fxRequest.add(it) }
        return fxRateService.getRates(fxRequest, tokenService.bearerToken).data.rates
    }

    private fun conversionRate(
        from: Currency,
        to: Currency,
        rates: Map<IsoCurrencyPair, FxRate>
    ): BigDecimal {
        if (from.code == to.code) return BigDecimal.ONE
        val rate = rates[IsoCurrencyPair(from.code, to.code)]?.rate
        if (rate == null) {
            log.warn(
                "No FX rate for {}:{} while aggregating cost; leaving unconverted",
                from.code,
                to.code
            )
            return BigDecimal.ONE
        }
        return rate
    }

    /**
     * For each contributing portfolio, record the asset quantity it holds and
     * attach that list to the matching aggregated position. Portfolios with
     * zero net quantity in an asset are skipped.
     */
    private fun applyPortfolioBreakdown(
        aggregated: Positions,
        perPortfolioPositions: List<Pair<Portfolio, Positions>>
    ) {
        if (!aggregated.hasPositions()) return

        val breakdownByAsset = mutableMapOf<String, MutableList<PortfolioBreakdown>>()
        perPortfolioPositions.forEach { (portfolio, positions) ->
            positions.positions.values.forEach { position ->
                val quantity = position.quantityValues.getTotal()
                if (quantity.signum() == 0) return@forEach
                breakdownByAsset
                    .getOrPut(position.asset.id) { mutableListOf() }
                    .add(
                        PortfolioBreakdown(
                            portfolioId = portfolio.id,
                            portfolioCode = portfolio.code,
                            portfolioName = portfolio.name,
                            quantity = quantity,
                            held = position.held.toMap()
                        )
                    )
            }
        }

        aggregated.positions.values.forEach { position ->
            breakdownByAsset[position.asset.id]?.let { position.portfolioBreakdown = it.toList() }
        }
    }
}

private fun Position.addQuantities(source: Position) {
    quantityValues.purchased = quantityValues.purchased.add(source.quantityValues.purchased)
    quantityValues.sold = quantityValues.sold.add(source.quantityValues.sold)
    quantityValues.adjustment = quantityValues.adjustment.add(source.quantityValues.adjustment)
    earmarkedQuantity = earmarkedQuantity.add(source.earmarkedQuantity)
}

/** Adds [source]'s cost and flow totals, converted at [rate]. */
private fun MoneyValues.addConverted(
    source: MoneyValues,
    rate: BigDecimal
) {
    costValue = costValue.add(source.costValue.convertedAt(rate))
    costBasis = costBasis.add(source.costBasis.convertedAt(rate))
    purchases = purchases.add(source.purchases.convertedAt(rate))
    sales = sales.add(source.sales.convertedAt(rate))
    dividends = dividends.add(source.dividends.convertedAt(rate))
    fees = fees.add(source.fees.convertedAt(rate))
    expenses = expenses.add(source.expenses.convertedAt(rate))
    realisedGain = realisedGain.add(source.realisedGain.convertedAt(rate))
}

private fun BigDecimal.convertedAt(rate: BigDecimal): BigDecimal = MathUtils.multiply(this, rate) ?: this

private fun Position.adoptEarliestDates(source: Position) {
    dateValues.firstTransaction = earliest(dateValues.firstTransaction, source.dateValues.firstTransaction)
    dateValues.opened = earliest(dateValues.opened, source.dateValues.opened)
}

private fun earliest(
    a: LocalDate?,
    b: LocalDate?
): LocalDate? =
    when {
        a == null -> b
        b == null -> a
        else -> if (a.isBefore(b)) a else b
    }