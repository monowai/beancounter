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
 * Shared by [TreasuryYieldService] (daily yields) and [EconomicCalendarService] (monthly
 * payrolls, unemployment and CPI). Split out so the `@Cacheable` boundary is a genuine cross-bean
 * call from every caller — request paths and [MacroRefreshSchedule]'s warm-up call reach this
 * method through the Spring proxy, so they share one cache entry per series rather than one caller
 * bypassing the cache via self-invocation.
 */
@Service
class FredSeriesFetcher(
    private val fredGateway: FredGateway,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(FredSeriesFetcher::class.java)

    /**
     * Observations for [seriesId] (a FRED series id, e.g. `DGS10` daily or `PAYEMS` monthly),
     * newest first, bounded to the last [WINDOW_DAYS] so the cached entry stays small. Rows with
     * no value (`.`, e.g. non-trading days) are filtered out.
     *
     * Body content never throws — a blank or malformed upstream body yields an empty list so
     * callers can treat "no data for this series" as a normal, omittable outcome. Transport
     * failures that survive the `providerHttp` retries do propagate; the callers contain them
     * ([MacroIndicatorsService] and [EconomicCalendarService] via `safely`,
     * [MacroRefreshSchedule] via `runCatching`).
     */
    @Cacheable(CACHE_NAME, key = "#seriesId")
    fun fetch(seriesId: String): List<SeriesPoint> {
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

    private fun toPoint(line: String): SeriesPoint? {
        val cells = line.trim().split(',')
        if (cells.size != 2) return null
        val (dateStr, valueStr) = cells
        val value = if (valueStr == NON_TRADING_DAY) null else valueStr.toBigDecimalOrNull()
        val date = value?.let { parseDate(dateStr) }
        return if (value == null || date == null) null else SeriesPoint(date, value)
    }

    private fun parseDate(dateStr: String): LocalDate? =
        try {
            LocalDate.parse(dateStr)
        } catch (
            @Suppress("SwallowedException")
            e: DateTimeParseException
        ) {
            null
        }

    companion object {
        private const val NON_TRADING_DAY = "."
        const val CACHE_NAME = "macro.fred.series"

        /**
         * Covers [TreasuryYieldService]'s 30-day chart points plus any `lookbackDays` a caller
         * passes, and [EconomicCalendarService]'s year-on-year prior: a monthly print lags its
         * reference month by up to ~2 months, and the prior YoY needs 13 months before that.
         */
        const val WINDOW_DAYS = 500L
    }
}