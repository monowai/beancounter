package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Computes a benchmark "Growth of 1000" series for a market index aligned to the
 * same monthly valuation grid the portfolio performance series uses, so the two
 * series overlay cleanly on a shared X axis.
 *
 * The series is rebased to BASE_VALUE (1000) at the first available close, with
 * each subsequent point computed as `(close / firstClose) * BASE_VALUE`.
 *
 * This is the foundation for future investment-modelling extensions: with the
 * same date grid, a synthetic-contribution variant ("if I'd put my actual cash
 * flows into ^GSPC") becomes a layering on top of the rebase logic, not a
 * separate code path.
 */
@Service
class BenchmarkService(
    private val priceService: PriceService,
    private val tokenService: TokenService,
    private val dateUtils: DateUtils
) {
    fun benchmark(
        portfolio: Portfolio,
        indexAssetId: String,
        months: Int = DEFAULT_MONTHS
    ): PerformanceResponse {
        val endDate = dateUtils.date
        val startDate = endDate.minusMonths(months.toLong())
        val dates = monthlyDates(startDate, endDate)

        val bulk =
            priceService.getBulkPrices(
                BulkPriceRequest(
                    dates = dates.map { it.toString() },
                    assets = listOf(PriceAsset(market = INDEX_MARKET, code = "", assetId = indexAssetId))
                ),
                tokenService.bearerToken
            )

        val closesByDate = closesByDate(bulk.data, indexAssetId)
        if (closesByDate.isEmpty()) {
            log.warn("No prices for index {} in [{} .. {}]", indexAssetId, startDate, endDate)
            return PerformanceResponse(PerformanceData(portfolio.currency, emptyList()))
        }

        val firstClose =
            firstAvailableClose(dates, closesByDate)
                ?: throw BusinessException("No reference close available for $indexAssetId from $startDate")

        val series =
            dates.mapNotNull { date ->
                val close = closeAtOrBefore(date, closesByDate) ?: return@mapNotNull null
                val growthOf1000 =
                    close
                        .divide(firstClose, SCALE, RoundingMode.HALF_UP)
                        .multiply(BASE_VALUE)
                        .setScale(2, RoundingMode.HALF_UP)
                val cumulativeReturn =
                    growthOf1000
                        .divide(BASE_VALUE, SCALE, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                PerformanceDataPoint(
                    date = date,
                    growthOf1000 = growthOf1000,
                    marketValue = BigDecimal.ZERO,
                    netContributions = BigDecimal.ZERO,
                    cumulativeReturn = cumulativeReturn,
                    cumulativeDividends = BigDecimal.ZERO
                )
            }

        return PerformanceResponse(PerformanceData(portfolio.currency, series))
    }

    /**
     * Monthly grid from [startDate] to [endDate] inclusive. When the anchor is
     * the last day of its month, every subsequent point is snapped back to the
     * last day of its month so the grid stays on month-ends rather than
     * drifting forward by a day after February. java.time's `plusMonths`
     * clamps day-of-month to the target month's max once, then keeps the
     * clamped day forever (e.g. 2026-03-31 → 2026-04-30 → 2026-05-30, never
     * recovers May 31). The snap reverses that drift.
     *
     * `endDate` is always present in the result; deduplicated via the set so
     * the grid lands exactly on `endDate` when it coincides with a month-end
     * point instead of being appended as an extra trailing element.
     */
    internal fun monthlyDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        val anchorIsMonthEnd = startDate == startDate.with(TemporalAdjusters.lastDayOfMonth())
        val dates = sortedSetOf<LocalDate>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            dates.add(cursor)
            val next = cursor.plusMonths(1)
            cursor = if (anchorIsMonthEnd) next.with(TemporalAdjusters.lastDayOfMonth()) else next
        }
        dates.add(endDate)
        return dates.toList()
    }

    private fun closesByDate(
        bulk: Map<String, Collection<com.beancounter.common.model.MarketData>>,
        indexAssetId: String
    ): Map<LocalDate, BigDecimal> =
        bulk
            .mapNotNull { (dateStr, mds) ->
                val close = mds.firstOrNull { it.asset.id == indexAssetId }?.close ?: return@mapNotNull null
                LocalDate.parse(dateStr) to close
            }.toMap()

    private fun firstAvailableClose(
        dates: List<LocalDate>,
        closes: Map<LocalDate, BigDecimal>
    ): BigDecimal? = dates.firstNotNullOfOrNull { closeAtOrBefore(it, closes) }

    private fun closeAtOrBefore(
        date: LocalDate,
        closes: Map<LocalDate, BigDecimal>
    ): BigDecimal? {
        closes[date]?.let { return it }
        return closes
            .filterKeys { !it.isAfter(date) }
            .maxByOrNull { it.key }
            ?.value
    }

    companion object {
        private const val INDEX_MARKET = "INDEX"
        private const val DEFAULT_MONTHS = 12
        private const val SCALE = 6
        private val BASE_VALUE = BigDecimal("1000")
        private val log = LoggerFactory.getLogger(BenchmarkService::class.java)
    }
}