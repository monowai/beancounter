package com.beancounter.position.service

import com.beancounter.common.contracts.AggregatedPerformanceData
import com.beancounter.common.contracts.AggregatedPerformanceDataPoint
import com.beancounter.common.contracts.AggregatedPerformanceResponse
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.Currency
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Portfolio
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.irr.TwrCalculator
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Pure composition step behind [PerformanceService.aggregate]. Combines per-portfolio TWR
 * series into a single composite series in a display currency. No IO.
 *
 * Algorithm (chained sub-period composite, GIPS):
 *   1. Build the union of valuation dates across portfolios.
 *   2. Forward-fill each portfolio's snapshots onto union dates.
 *   3. Convert market value / contributions / dividends to display ccy.
 *   4. For each sub-period [t_{i-1}, t_i]: sub-return = Σ w_p * r_p,
 *      where w_p = mv_p[i-1] / Σ mv[i-1] (display-ccy AUM) and
 *      r_p = growthFactor_p[i] / growthFactor_p[i-1] (TWR factor ratio).
 *      Portfolios with mv_p[i-1] == 0 are excluded from this sub-period.
 *   5. cumulative_factor[i] = cumulative_factor[i-1] * sub-return.
 *
 * Period-relative metrics (`netContributions`, `cumulativeDividends`,
 * `investmentGain`) are baselined against the first union point, so
 * `series[0]` reports zero for them.
 */
@Component
class AggregatePerformanceComposer(
    private val irrCalculator: IrrCalculator
) {
    fun compose(
        perPortfolio: List<Pair<Portfolio, PerformanceData>>,
        displayCurrency: Currency,
        fxRates: Map<String, BigDecimal>
    ): AggregatedPerformanceResponse {
        val perPortfolioFx = toDisplaySeries(perPortfolio, fxRates)
        if (perPortfolioFx.isEmpty()) {
            return AggregatedPerformanceResponse(AggregatedPerformanceData(displayCurrency))
        }

        val sortedDates =
            perPortfolioFx
                .flatMap { snaps -> snaps.map { it.date } }
                .toSortedSet()
                .toList()
        val aligned = perPortfolioFx.map { alignToDates(it, sortedDates) }
        val totals = sortedDates.indices.map { i -> totalAt(aligned, i) }
        val cumFactor = chainedFactors(aligned, sortedDates.size)

        val points = sortedDates.indices.map { i -> toPoint(sortedDates[i], totals[i], totals[0], cumFactor[i]) }
        return AggregatedPerformanceResponse(
            AggregatedPerformanceData(currency = displayCurrency, series = points, xirr = xirr(perPortfolioFx))
        )
    }

    /**
     * Discard empty per-portfolio series. Sort each surviving series by date and convert it to
     * the display currency once.
     */
    private fun toDisplaySeries(
        perPortfolio: List<Pair<Portfolio, PerformanceData>>,
        fxRates: Map<String, BigDecimal>
    ): List<List<DisplaySnapshot>> =
        perPortfolio
            .filter { (_, data) -> data.series.isNotEmpty() }
            .map { (portfolio, data) ->
                val rate = fxRates[portfolio.currency.code] ?: BigDecimal.ONE
                data.series
                    .sortedBy { it.date }
                    .map { p ->
                        DisplaySnapshot(
                            date = p.date,
                            growthFactor =
                                p.growthOf1000
                                    .divide(TwrCalculator.GROWTH_BASE, GROWTH_FACTOR_SCALE, RoundingMode.HALF_UP),
                            mv = p.marketValue.multiply(rate),
                            contrib = p.netContributions.multiply(rate),
                            divs = p.cumulativeDividends.multiply(rate)
                        )
                    }
            }

    /**
     * Forward-fill one portfolio's snapshots onto the union dates. A portfolio with no snapshot
     * on/before a union date is "inactive" there (mv contribution 0, excluded from weighting).
     */
    private fun alignToDates(
        snaps: List<DisplaySnapshot>,
        dates: List<LocalDate>
    ): List<DisplaySnapshot?> {
        var cursor = -1
        return dates.map { date ->
            while (cursor + 1 < snaps.size && !snaps[cursor + 1].date.isAfter(date)) {
                cursor++
            }
            if (cursor < 0) null else snaps[cursor]
        }
    }

    /** Display-ccy totals across the active portfolios on union date [i]. */
    private fun totalAt(
        aligned: List<List<DisplaySnapshot?>>,
        i: Int
    ): AggregateRow {
        val active = aligned.mapNotNull { it[i] }
        return AggregateRow(
            mv = active.fold(BigDecimal.ZERO) { sum, s -> sum.add(s.mv) },
            contrib = active.fold(BigDecimal.ZERO) { sum, s -> sum.add(s.contrib) },
            divs = active.fold(BigDecimal.ZERO) { sum, s -> sum.add(s.divs) }
        )
    }

    /** Chained sub-period composite TWR factor per union date, starting at 1. */
    private fun chainedFactors(
        aligned: List<List<DisplaySnapshot?>>,
        size: Int
    ): List<BigDecimal> {
        val cumFactor = MutableList(size) { BigDecimal.ONE }
        for (i in 1 until size) {
            cumFactor[i] = cumFactor[i - 1].multiply(subPeriodReturn(aligned, i))
        }
        return cumFactor
    }

    /**
     * Market-value weighted return of the portfolios active across the sub-period ending at
     * union date [i]. One when no portfolio carries weight into it.
     */
    private fun subPeriodReturn(
        aligned: List<List<DisplaySnapshot?>>,
        i: Int
    ): BigDecimal {
        var numerator = BigDecimal.ZERO
        var weightSum = BigDecimal.ZERO
        for (p in aligned) {
            val prev = p[i - 1]?.takeIf { it.mv.signum() > 0 && it.growthFactor.signum() > 0 }
            val curr = p[i]
            if (prev != null && curr != null) {
                val r = curr.growthFactor.divide(prev.growthFactor, SUB_PERIOD_RETURN_SCALE, RoundingMode.HALF_UP)
                numerator = numerator.add(prev.mv.multiply(r))
                weightSum = weightSum.add(prev.mv)
            }
        }
        return if (weightSum.signum() > 0) {
            numerator.divide(weightSum, SUB_PERIOD_RETURN_SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ONE
        }
    }

    private fun toPoint(
        date: LocalDate,
        row: AggregateRow,
        baseline: AggregateRow,
        cumFactor: BigDecimal
    ): AggregatedPerformanceDataPoint {
        val periodContrib = row.contrib.subtract(baseline.contrib)
        val periodDivs = row.divs.subtract(baseline.divs)
        val investmentGain =
            row.mv
                .subtract(baseline.mv)
                .subtract(periodContrib)
        return AggregatedPerformanceDataPoint(
            date = date,
            growthOf1000 =
                cumFactor
                    .multiply(
                        TwrCalculator.GROWTH_BASE
                    ).setScale(GROWTH_OF_1000_SCALE, RoundingMode.HALF_UP),
            cumulativeReturn =
                cumFactor
                    .subtract(BigDecimal.ONE)
                    .setScale(AGGREGATE_RETURN_SCALE, RoundingMode.HALF_UP),
            marketValue = row.mv.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            netContributions = periodContrib.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            lifetimeContributions = row.contrib.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            cumulativeDividends = periodDivs.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            investmentGain = investmentGain.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        )
    }

    /**
     * Pool per-portfolio investor cash flows over the window into a single
     * `PeriodicCashFlows` and solve for XIRR via the shared `IrrCalculator`.
     *
     * Sign convention is investor-POV:
     *   - At series[0] (window start): −marketValue (capital invested).
     *   - Between snapshots: −Δ netContributions (deposit = money out of wallet).
     *   - At series[last] (window end): +marketValue (capital realised).
     *
     * Inputs are already FX-converted to the display currency, so all flows
     * land in a single denomination. `IrrCalculator` itself handles short-
     * window fallback to simple ROI and bails to 0.0 when the solver fails.
     */
    private fun xirr(perPortfolioFx: List<List<DisplaySnapshot>>): BigDecimal? {
        val flows = mutableListOf<CashFlow>()
        for (snaps in perPortfolioFx) {
            val first = snaps.first()
            if (first.mv.signum() > 0) {
                flows.add(CashFlow(amount = first.mv.negate().toDouble(), date = first.date))
            }
            for (i in 1 until snaps.size) {
                val delta = snaps[i].contrib.subtract(snaps[i - 1].contrib)
                if (delta.signum() != 0) {
                    flows.add(CashFlow(amount = delta.negate().toDouble(), date = snaps[i].date))
                }
            }
            val last = snaps.last()
            if (last.mv.signum() > 0) {
                flows.add(CashFlow(amount = last.mv.toDouble(), date = last.date))
            }
        }
        if (flows.size < 2) return null
        val periodic = PeriodicCashFlows()
        periodic.addAll(flows)
        return BigDecimal(irrCalculator.calculate(periodic)).setScale(XIRR_SCALE, RoundingMode.HALF_UP)
    }

    private data class DisplaySnapshot(
        val date: LocalDate,
        val growthFactor: BigDecimal,
        val mv: BigDecimal,
        val contrib: BigDecimal,
        val divs: BigDecimal
    )

    private data class AggregateRow(
        val mv: BigDecimal,
        val contrib: BigDecimal,
        val divs: BigDecimal
    )

    private companion object {
        const val GROWTH_FACTOR_SCALE = 8
        const val SUB_PERIOD_RETURN_SCALE = 10
        const val GROWTH_OF_1000_SCALE = 4
        const val AGGREGATE_RETURN_SCALE = 8
        const val XIRR_SCALE = 6
        const val MONEY_SCALE = 2
    }
}