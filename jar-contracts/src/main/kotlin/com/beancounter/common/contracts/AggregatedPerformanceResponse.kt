package com.beancounter.common.contracts

import com.beancounter.common.model.Currency
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One row of an aggregated, period-relative performance series for a set of
 * portfolios converted to a single display currency.
 *
 * Period-relative fields (`netContributions`, `cumulativeDividends`,
 * `investmentGain`) are baselined from the first point in the requested window
 * — series[0] returns zero for these. `lifetimeContributions` carries the
 * inception-to-date total at this date for tooltip / context use.
 *
 * Composite TWR (`growthOf1000`, `cumulativeReturn`) is chained sub-period
 * with beginning-of-sub-period AUM weights in display currency, per GIPS.
 */
data class AggregatedPerformanceDataPoint(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val date: LocalDate,
    val growthOf1000: BigDecimal = BigDecimal.ZERO,
    val cumulativeReturn: BigDecimal = BigDecimal.ZERO,
    val marketValue: BigDecimal = BigDecimal.ZERO,
    val netContributions: BigDecimal = BigDecimal.ZERO,
    val lifetimeContributions: BigDecimal = BigDecimal.ZERO,
    val cumulativeDividends: BigDecimal = BigDecimal.ZERO,
    val investmentGain: BigDecimal = BigDecimal.ZERO
)

data class AggregatedPerformanceData(
    val currency: Currency,
    val series: List<AggregatedPerformanceDataPoint> = emptyList(),
    /**
     * Aggregate money-weighted return (XIRR) for the requested window, as an
     * annualised decimal (0.10 = +10% p.a.). Null when the solver could not
     * converge or there were insufficient flows. For windows shorter than
     * ~1 year, IrrCalculator falls back to simple ROI on the pooled flows;
     * consumers should label sub-year results as "cumulative", not "p.a."
     */
    val xirr: BigDecimal? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val firstTradeDate: LocalDate? = null
)

data class AggregatedPerformanceResponse(
    override val data: AggregatedPerformanceData
) : Payload<AggregatedPerformanceData>

data class AggregatedPerformanceRequest(
    val portfolioCodes: List<String>,
    val months: Int = 12,
    val displayCurrency: String
)