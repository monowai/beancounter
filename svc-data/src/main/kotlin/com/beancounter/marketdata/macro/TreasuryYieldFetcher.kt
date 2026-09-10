package com.beancounter.marketdata.macro

import com.beancounter.marketdata.providers.alpha.AlphaGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Cached fetch + parse layer over AlphaVantage's TREASURY_YIELD endpoint.
 *
 * Split out from [TreasuryYieldService] so the `@Cacheable` boundary is a genuine cross-bean call
 * from every caller — both the `/macro/indicators` request path and [MacroRefreshSchedule]'s
 * warm-up call reach this method through the Spring proxy, so they share one cache entry per
 * maturity rather than one caller bypassing the cache via self-invocation.
 */
@Service
class TreasuryYieldFetcher(
    private val alphaGateway: AlphaGateway,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(TreasuryYieldFetcher::class.java)

    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    /**
     * Daily yield-curve points for [maturity] (AlphaVantage's maturity code, e.g. `10year`,
     * `2year`), newest first. Non-trading-day rows (`value: "."`) are filtered out. Never throws —
     * a parse failure or blank upstream response yields an empty list so callers can treat "no
     * data for this maturity" as a normal, omittable outcome.
     */
    @Cacheable("alpha.treasury.yield", key = "#maturity")
    fun fetch(maturity: String): List<YieldPoint> {
        val json = alphaGateway.getTreasuryYield(interval = "daily", maturity = maturity, apiKey = apiKey)
        if (json.isBlank()) return emptyList()
        val raw = parse(json) ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val rows = raw["data"] as? List<Map<String, Any>> ?: return emptyList()
        return rows
            .mapNotNull { toPoint(it) }
            .sortedByDescending { it.date }
    }

    private fun toPoint(row: Map<String, Any>): YieldPoint? {
        val dateStr = row["date"] as? String ?: return null
        val valueStr = row["value"] as? String ?: return null
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

    private fun parse(json: String): Map<String, Any>? =
        try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        } catch (
            // Provider JSON is untrusted; malformed payload or shape mismatch → drop the feed.
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Failed to parse treasury yield feed: {}", e.message)
            null
        }

    companion object {
        private const val NON_TRADING_DAY = "."
    }
}