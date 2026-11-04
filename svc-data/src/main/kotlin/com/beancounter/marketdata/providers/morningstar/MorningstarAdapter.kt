package com.beancounter.marketdata.providers.morningstar

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.providers.morningstar.MorningstarPriceService.Companion.ID
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Morningstar time-series JSON as price rows: the latest NAV, or the whole history for backfill.
 */
internal object MorningstarAdapter {
    private val log = LoggerFactory.getLogger(MorningstarAdapter::class.java)
    private val objectMapper = BcJson.objectMapper
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Parse Morningstar JSON response and extract the latest price.
     * Response format:
     * {
     *   "TimeSeries": {
     *     "Security": [{
     *       "HistoryDetail": [
     *         {"EndDate": "2024-12-18", "Value": 3.4567}
     *       ]
     *     }]
     *   }
     * }
     */
    fun latestPrice(
        json: String,
        asset: Asset
    ): MarketData? =
        try {
            val root = objectMapper.readTree(json)
            val securities = root.path("TimeSeries").path("Security")

            if (securities.isEmpty || !securities.isArray) {
                log.debug("No securities found in response for {}", asset.code)
                null
            } else {
                parseLatestPrice(securities[0].path("HistoryDetail"), asset)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Return null on parsing failure
            log.error("Error parsing Morningstar response for {}: {}", asset.code, e.message)
            null
        }

    private fun parseLatestPrice(
        historyDetail: JsonNode,
        asset: Asset
    ): MarketData? {
        if (historyDetail.isEmpty || !historyDetail.isArray || historyDetail.size() == 0) {
            log.debug("No history detail found for {}", asset.code)
            return null
        }

        // Get the most recent price (last element)
        val marketData = toMarketData(historyDetail[historyDetail.size() - 1], asset)
        log.debug("Parsed price for {}: {} on {}", asset.code, marketData.close, marketData.priceDate)
        return marketData
    }

    /** One HistoryDetail node as a price row. */
    private fun toMarketData(
        priceNode: JsonNode,
        asset: Asset
    ): MarketData =
        MarketData(
            asset = asset,
            priceDate = LocalDate.parse(priceNode.path("EndDate").asString(), DATE_FORMATTER),
            close = BigDecimal(priceNode.path("Value").asString()),
            source = ID
        )

    /**
     * Parse all historical prices from the response.
     */
    fun history(
        json: String,
        asset: Asset
    ): List<MarketData> =
        try {
            val root = objectMapper.readTree(json)
            val securities = root.path("TimeSeries").path("Security")

            if (securities.isEmpty || !securities.isArray) {
                emptyList()
            } else {
                parseHistoryDetail(securities[0].path("HistoryDetail"), asset)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Return empty list on parsing failure
            log.error("Error parsing historical response for {}: {}", asset.code, e.message)
            emptyList()
        }

    private fun parseHistoryDetail(
        historyDetail: JsonNode,
        asset: Asset
    ): List<MarketData> {
        if (historyDetail.isEmpty || !historyDetail.isArray) {
            return emptyList()
        }

        return historyDetail.mapNotNull { priceNode ->
            try {
                toMarketData(priceNode, asset)
            } catch (
                @Suppress("TooGenericExceptionCaught")
                _: Exception
            ) {
                null
            }
        }
    }
}