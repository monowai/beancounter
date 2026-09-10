package com.beancounter.marketdata.macro

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Composes the `/macro/indicators` response: treasury yields (via [TreasuryYieldService]) plus
 * oil-proxy price moves (WTI/Brent ETF proxies, via EODHD EOD history).
 *
 * Every leg is fetched independently and wrapped so one upstream failure never 500s the whole
 * request — a failing yield or oil leg is simply omitted from its list, matching the
 * `/macro/indicators` contract ("entries that fail upstream are omitted, not null").
 */
@Service
class MacroIndicatorsService(
    private val eodhdProxy: EodhdProxy,
    private val eodhdConfig: EodhdConfig,
    private val treasuryYieldService: TreasuryYieldService,
    private val dateUtils: DateUtils = DateUtils()
) {
    fun getIndicators(lookbackDays: Int = DEFAULT_LOOKBACK_DAYS): MacroIndicatorsResponse {
        val yields =
            safely("treasury yields") { treasuryYieldService.getYields(lookbackDays) }.orEmpty()
        val oil =
            OIL_PROXIES.mapNotNull { (series, symbol) ->
                safely("oil leg $series") { fetchOil(series, symbol, lookbackDays) }
            }
        return MacroIndicatorsResponse(
            asOf = dateUtils.date,
            lookbackDays = lookbackDays,
            yields = yields,
            oil = oil
        )
    }

    private fun fetchOil(
        series: String,
        symbol: String,
        lookbackDays: Int
    ): OilSeries? {
        val today = dateUtils.date
        val from = today.minusDays((lookbackDays + OIL_WINDOW_BUFFER_DAYS).toLong())
        val prices =
            eodhdProxy.getHistory(
                symbol = "$symbol.US",
                dateFrom = from.toString(),
                dateTo = today.toString(),
                apiKey = eodhdConfig.apiKey
            )
        if (prices.isEmpty()) return null

        val sorted = prices.sortedByDescending { it.date }
        val latest = sorted.first()
        val lookback = nearest(sorted, latest.date.minusDays(lookbackDays.toLong())) ?: return null

        return OilSeries(
            series = series,
            symbol = symbol,
            latest = latest.close,
            lookback = lookback.close,
            changePercent = changePercent(latest.close, lookback.close)
        )
    }

    private fun changePercent(
        latest: BigDecimal,
        lookback: BigDecimal
    ): BigDecimal {
        if (lookback.signum() == 0) return BigDecimal.ZERO
        return latest
            .subtract(lookback)
            .divide(lookback, DIVISION_CONTEXT)
            .multiply(BigDecimal(100))
            .setScale(CHANGE_SCALE, RoundingMode.HALF_UP)
    }

    private fun nearest(
        prices: List<EodhdPrice>,
        target: java.time.LocalDate
    ): EodhdPrice? = prices.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, target)) }

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
            log.warn("Macro indicator fetch failed ({}): {}", label, e.message)
            null
        }

    companion object {
        private val log = LoggerFactory.getLogger(MacroIndicatorsService::class.java)
        const val DEFAULT_LOOKBACK_DAYS = 14
        private const val OIL_WINDOW_BUFFER_DAYS = 20
        private const val CHANGE_SCALE = 4
        private val DIVISION_CONTEXT = MathContext(12)

        // Labels are what these ETFs proxy, not the provider ticker convention — the response
        // contract explicitly forbids exposing provider names; WTI_PROXY/BRENT_PROXY + USO/BNO
        // are the intentionally-public exception (they're the underlying instrument, not EODHD).
        private val OIL_PROXIES =
            listOf(
                "WTI_PROXY" to "USO",
                "BRENT_PROXY" to "BNO"
            )
    }
}