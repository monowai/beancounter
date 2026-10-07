package com.beancounter.marketdata.macro

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Cached fetch + parse layer over FRED's `fredgraph.csv` endpoint.
 *
 * Split out from [TreasuryYieldService] so the `@Cacheable` boundary is a genuine cross-bean call
 * from every caller — both the `/macro/indicators` request path and [MacroRefreshSchedule]'s
 * warm-up call reach this method through the Spring proxy, so they share one cache entry per
 * series rather than one caller bypassing the cache via self-invocation.
 */
@Service
class TreasuryYieldFetcher(
    private val fredGateway: FredGateway,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(TreasuryYieldFetcher::class.java)

    /**
     * Daily yield-curve points for [seriesId] (a FRED series id, e.g. `DGS10`, `DGS2`), newest
     * first, bounded to the last [WINDOW_DAYS] so the cached entry stays small. Non-trading-day
     * rows (value `.`) are filtered out.
     *
     * Body content never throws — a blank or malformed upstream body yields an empty list so
     * callers can treat "no data for this series" as a normal, omittable outcome. Transport
     * failures that survive the `providerHttp` retries do propagate; the callers contain them
     * ([MacroIndicatorsService] via `safely`, [MacroRefreshSchedule] via `runCatching`).
     */
    @Cacheable("macro.treasury.yield", key = "#seriesId")
    fun fetch(seriesId: String): List<YieldPoint> {
        val csv = fredGateway.getSeriesCsv(seriesId, dateUtils.date.minusDays(WINDOW_DAYS))
        val points =
            csv
                .lineSequence()
                .drop(1) // observation_date,{seriesId} header
                .mapNotNull { toPoint(it) }
                .sortedByDescending { it.date }
                .toList()
        if (points.isEmpty() && csv.isNotBlank()) {
            log.warn("FRED series {} returned a body with no parseable rows", seriesId)
        }
        return points
    }

    private fun toPoint(line: String): YieldPoint? {
        val cells = line.trim().split(',')
        if (cells.size != 2) return null
        val (dateStr, valueStr) = cells
        if (valueStr == NON_TRADING_DAY) return null
        val value = valueStr.toBigDecimalOrNull() ?: return null
        val date =
            try {
                LocalDate.parse(dateStr)
            } catch (
                @Suppress("SwallowedException")
                e: DateTimeParseException
            ) {
                return null
            }
        return YieldPoint(date, value)
    }

    companion object {
        private const val NON_TRADING_DAY = "."

        /**
         * Covers [TreasuryYieldService]'s 30-day chart points plus any `lookbackDays` a caller
         * passes, with slack for holiday gaps.
         */
        const val WINDOW_DAYS = 400L
    }
}