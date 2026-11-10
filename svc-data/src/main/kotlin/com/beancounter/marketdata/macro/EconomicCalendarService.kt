package com.beancounter.marketdata.macro

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.HUNDRED
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Composes the `/macro/calendar` response: the latest US payrolls, unemployment and CPI prints
 * (from FRED monthly series via [FredSeriesFetcher]) and the next scheduled release of each, plus
 * the next Fed decision (from Kalshi event markets via [KalshiGateway]).
 *
 * An upcoming release's date is its Kalshi market close — markets close one minute before the
 * figure is published. Its expectation is the market-implied median: the strike at which the
 * "Above X" probability ladder crosses 50%, interpolated linearly between neighbouring strikes.
 *
 * Every indicator and every release is fetched independently and wrapped, so one upstream failure
 * only removes that entry — matching the `/macro/indicators` contract. Responses never name the
 * upstream providers.
 */
@Service
class EconomicCalendarService(
    private val fredSeriesFetcher: FredSeriesFetcher,
    private val kalshiGateway: KalshiGateway,
    private val dateUtils: DateUtils = DateUtils()
) {
    @Cacheable(CACHE_NAME)
    fun getCalendar(): EconomicCalendarResponse {
        val now = OffsetDateTime.now(dateUtils.zoneId)
        return EconomicCalendarResponse(
            asOf = dateUtils.date,
            country = COUNTRY,
            recent = PRINTS.mapNotNull { safely(it.indicator) { latestPrint(it) } },
            upcoming =
                RELEASES
                    .mapNotNull { safely(it.indicator) { nextRelease(it, now) } }
                    .sortedBy { it.releaseDate }
        )
    }

    private fun latestPrint(spec: PrintSpec): EconomicPrint? {
        val byMonth = fredSeriesFetcher.fetch(spec.seriesId).associate { YearMonth.from(it.date) to it.value }
        return byMonth.keys.maxOrNull()?.let { latest ->
            spec.transform.at(byMonth, latest)?.let { value ->
                EconomicPrint(
                    indicator = spec.indicator,
                    label = spec.label,
                    unit = spec.unit,
                    period = latest.atDay(1),
                    value = value,
                    prior = spec.transform.at(byMonth, latest.minusMonths(1))
                )
            }
        }
    }

    private fun nextRelease(
        spec: ReleaseSpec,
        now: OffsetDateTime
    ): UpcomingRelease? {
        val candidate = kalshiGateway.nearestOpenEvent(spec.seriesTicker, now) ?: return null
        return UpcomingRelease(
            indicator = spec.indicator,
            label = spec.label,
            unit = spec.unit,
            releaseDate = candidate.closeTime.atZoneSameInstant(US_EASTERN).toLocalDate(),
            period = if (spec.hasReferencePeriod) referencePeriod(candidate.event.eventTicker) else null,
            expected = spec.ladder?.let { impliedMedian(candidate.markets, it) }
        )
    }

    /**
     * The strike where P(outcome > strike) crosses 0.5, interpolated between the two neighbouring
     * strikes. Null when the ladder never brackets 0.5 (the median lies outside the listed strikes).
     */
    private fun impliedMedian(
        markets: List<KalshiMarket>,
        ladder: Ladder
    ): BigDecimal? {
        val rungs =
            markets
                .filter { it.strikeType == STRIKE_ABOVE }
                .mapNotNull { m -> m.floorStrike?.let { strike -> m.impliedProbability()?.let { strike to it } } }
                .sortedBy { it.first }
        val crossing = rungs.zipWithNext().firstOrNull { (lo, hi) -> lo.second >= HALF && hi.second < HALF }
        return crossing?.let { (lo, hi) ->
            val fraction = (lo.second - HALF).divide(lo.second - hi.second, MATH_CONTEXT)
            val strike = lo.first + (hi.first - lo.first) * fraction
            strike.divide(ladder.divisor, MATH_CONTEXT).setScale(ladder.scale, RoundingMode.HALF_UP)
        }
    }

    /** `KXCPI-26NOV` → 2026-11-01. Null when the ticker doesn't end in a `-YYMON` suffix. */
    private fun referencePeriod(eventTicker: String): LocalDate? =
        try {
            YearMonth.parse(eventTicker.substringAfterLast('-'), TICKER_MONTH).atDay(1)
        } catch (
            @Suppress("SwallowedException")
            e: DateTimeParseException
        ) {
            null
        }

    /** Runs [block], logging and swallowing any failure so one bad leg never 500s the endpoint. */
    private fun <T> safely(
        label: String,
        block: () -> T
    ): T? =
        try {
            block()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Economic calendar fetch failed ({}): {}", label, e.message)
            null
        }

    /** How a print's value is derived from a monthly series, evaluated at a given month. */
    private enum class Transform {
        LEVEL,
        CHANGE,
        PERCENT_MOM,
        PERCENT_YOY;

        fun at(
            byMonth: Map<YearMonth, BigDecimal>,
            month: YearMonth
        ): BigDecimal? {
            val current = byMonth[month] ?: return null
            return when (this) {
                LEVEL -> current
                CHANGE -> byMonth[month.minusMonths(1)]?.let { current - it }
                PERCENT_MOM -> byMonth[month.minusMonths(1)]?.let { percentChange(current, it) }
                PERCENT_YOY -> byMonth[month.minusYears(1)]?.let { percentChange(current, it) }
            }
        }

        private fun percentChange(
            current: BigDecimal,
            base: BigDecimal
        ): BigDecimal? =
            if (base.signum() == 0) {
                null
            } else {
                (current - base)
                    .divide(
                        base,
                        MATH_CONTEXT
                    ).multiply(HUNDRED)
                    .setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
            }
    }

    private data class PrintSpec(
        val indicator: String,
        val label: String,
        val unit: String,
        val seriesId: String,
        val transform: Transform
    )

    /** Converts a Kalshi strike into the print's unit: divide by [divisor], round to [scale]. */
    private data class Ladder(
        val divisor: BigDecimal = BigDecimal.ONE,
        val scale: Int = PERCENT_SCALE
    )

    private data class ReleaseSpec(
        val indicator: String,
        val label: String,
        val unit: String?,
        val seriesTicker: String,
        val ladder: Ladder?,
        val hasReferencePeriod: Boolean = true
    )

    companion object {
        private val log = LoggerFactory.getLogger(EconomicCalendarService::class.java)
        const val CACHE_NAME = "macro.economic.calendar"

        private const val COUNTRY = "US"
        private const val STRIKE_ABOVE = "greater"
        private const val PERCENT_SCALE = 2
        private const val UNIT_PERCENT = "%"
        private const val UNIT_THOUSANDS = "thousands"
        private val HALF = BigDecimal("0.5")
        private val MATH_CONTEXT = MathContext(12)
        private val THOUSAND = BigDecimal(1000)
        private val US_EASTERN = ZoneId.of("America/New_York")
        private val TICKER_MONTH =
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("yyMMM")
                .toFormatter(Locale.ENGLISH)

        private const val PAYROLLS = "NONFARM_PAYROLLS"
        private const val UNEMPLOYMENT = "UNEMPLOYMENT_RATE"
        private const val CPI_MOM = "CPI_MOM"
        private const val CPI_YOY = "CPI_YOY"

        // Headline CPI YoY is quoted on the unadjusted index; month-on-month on the adjusted one.
        private val PRINTS =
            listOf(
                PrintSpec(PAYROLLS, "Nonfarm payrolls, monthly change", UNIT_THOUSANDS, "PAYEMS", Transform.CHANGE),
                PrintSpec(UNEMPLOYMENT, "Unemployment rate", UNIT_PERCENT, "UNRATE", Transform.LEVEL),
                PrintSpec(CPI_MOM, "CPI, month on month", UNIT_PERCENT, "CPIAUCSL", Transform.PERCENT_MOM),
                PrintSpec(CPI_YOY, "CPI, year on year", UNIT_PERCENT, "CPIAUCNS", Transform.PERCENT_YOY),
                PrintSpec("CORE_CPI_YOY", "Core CPI, year on year", UNIT_PERCENT, "CPILFENS", Transform.PERCENT_YOY)
            )

        private val RELEASES =
            listOf(
                ReleaseSpec(
                    PAYROLLS,
                    "Nonfarm payrolls",
                    UNIT_THOUSANDS,
                    "KXPAYROLLS",
                    Ladder(divisor = THOUSAND, scale = 0)
                ),
                ReleaseSpec(UNEMPLOYMENT, "Unemployment rate", UNIT_PERCENT, "KXU3", Ladder()),
                ReleaseSpec(CPI_MOM, "CPI, month on month", UNIT_PERCENT, "KXCPI", Ladder()),
                ReleaseSpec(CPI_YOY, "CPI, year on year", UNIT_PERCENT, "KXCPIYOY", Ladder()),
                ReleaseSpec(
                    "FOMC_DECISION",
                    "Fed rate decision",
                    null,
                    RateExpectationsService.FED_DECISION_SERIES,
                    ladder = null,
                    hasReferencePeriod = false
                )
            )
    }
}