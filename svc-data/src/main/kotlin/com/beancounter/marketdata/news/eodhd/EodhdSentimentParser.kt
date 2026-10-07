package com.beancounter.marketdata.news.eodhd

import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * One day of aggregated sentiment as served by `GET /news/sentiment` and parsed from EODHD.
 * [normalized] is EODHD's score in `[-1, 1]`; [count] the number of articles behind it.
 */
data class SentimentPoint(
    val date: LocalDate,
    val count: Int,
    val normalized: BigDecimal
)

/**
 * Parses the EODHD `/api/sentiments` payload:
 *
 * ```
 * {"AAPL.US":[{"date":"2026-10-06","count":27,"normalized":0.8937}, …], "TSCO.LSE":[…]}
 * ```
 *
 * The map only carries symbols EODHD has coverage for — a requested ticker with no articles is
 * simply absent, which is not an error. Keys are upper-cased so a caller can match them against
 * its own `CODE.EXCHANGE` tickers regardless of how EODHD echoes the symbol. Individual malformed points (non-numeric `normalized`,
 * unparseable `date`) are dropped with a WARN so one bad element never costs the whole batch;
 * a body that is not a JSON object at all throws [IllegalArgumentException] so the caller can
 * count the batch as failed.
 */
object EodhdSentimentParser {
    private val log = LoggerFactory.getLogger(EodhdSentimentParser::class.java)
    private val mapper = JsonMapper.builder().build()

    fun parse(json: String): Map<String, List<SentimentPoint>> {
        val root =
            try {
                mapper.readTree(json)
            } catch (e: JacksonException) {
                throw IllegalArgumentException("Unparseable sentiment payload", e)
            }
        require(root.isObject) { "Sentiment payload is not a JSON object: ${json.take(PAYLOAD_EXCERPT)}" }
        return root.properties().associate { (symbol, node) -> symbol.uppercase() to points(symbol, node) }
    }

    private fun points(
        symbol: String,
        node: JsonNode
    ): List<SentimentPoint> {
        if (!node.isArray) {
            log.warn("Sentiment for {} is not an array: {}", symbol, node)
            return emptyList()
        }
        return node.mapNotNull { point(symbol, it) }
    }

    private fun point(
        symbol: String,
        node: JsonNode
    ): SentimentPoint? {
        val normalized = node.path("normalized")
        if (!normalized.isNumber) {
            log.warn("Dropping sentiment point for {} with non-numeric normalized: {}", symbol, node)
            return null
        }
        val date =
            try {
                LocalDate.parse(node.path("date").asString())
            } catch (e: DateTimeParseException) {
                log.warn("Dropping sentiment point for {} with unparseable date: {}", symbol, e.message)
                return null
            }
        val count = node.path("count")
        if (!count.isNumber) {
            log.warn("Dropping sentiment point for {} with non-numeric count: {}", symbol, node)
            return null
        }
        return SentimentPoint(date, count.asInt(), normalized.decimalValue())
    }

    /** Characters of a rejected body carried in the exception so a batch failure log is diagnosable. */
    private const val PAYLOAD_EXCERPT = 120
}