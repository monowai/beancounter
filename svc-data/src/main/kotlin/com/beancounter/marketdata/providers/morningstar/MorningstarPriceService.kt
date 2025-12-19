package com.beancounter.marketdata.providers.morningstar

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Morningstar price provider for UK/EU mutual funds.
 * Fetches NAV prices from Morningstar's unofficial API.
 */
@Service
class MorningstarPriceService(
    private val morningstarConfig: MorningstarConfig
) : MarketDataPriceProvider {
    private lateinit var morningstarProxy: MorningstarProxy
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val ID = "MORNINGSTAR"
        private val log = LoggerFactory.getLogger(MorningstarPriceService::class.java)
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    @Autowired(required = false)
    fun setMorningstarProxy(proxy: MorningstarProxy) {
        this.morningstarProxy = proxy
    }

    @PostConstruct
    fun logStatus() {
        log.info("Morningstar provider initialized for markets: {}", morningstarConfig.markets)
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val results = mutableListOf<MarketData>()

        for ((_, _, resolvedAsset) in priceRequest.assets) {
            if (resolvedAsset == null) continue

            val priceCode = morningstarConfig.getPriceCode(resolvedAsset)
            val currencyId = resolvedAsset.market.currency.code

            try {
                val json = morningstarProxy.getPrice(priceCode, currencyId)
                if (json != null) {
                    val marketData = parseResponse(json, resolvedAsset)
                    if (marketData != null) {
                        results.add(marketData)
                    }
                }
            } catch (e: Exception) {
                log.error("Error fetching price for {}: {}", priceCode, e.message)
            }
        }

        return results
    }

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
    private fun parseResponse(
        json: String,
        asset: Asset
    ): MarketData? {
        return try {
            val root = objectMapper.readTree(json)
            val securities = root.path("TimeSeries").path("Security")

            if (securities.isEmpty || !securities.isArray) {
                log.debug("No securities found in response for {}", asset.code)
                return null
            }

            val historyDetail = securities[0].path("HistoryDetail")
            if (historyDetail.isEmpty || !historyDetail.isArray || historyDetail.size() == 0) {
                log.debug("No history detail found for {}", asset.code)
                return null
            }

            // Get the most recent price (last element)
            val latestPrice = historyDetail[historyDetail.size() - 1]
            val priceDate = LocalDate.parse(latestPrice.path("EndDate").asText(), DATE_FORMATTER)
            val closePrice = BigDecimal(latestPrice.path("Value").asText())

            log.debug("Parsed price for {}: {} on {}", asset.code, closePrice, priceDate)

            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = closePrice,
                source = ID
            )
        } catch (e: Exception) {
            log.error("Error parsing Morningstar response for {}: {}", asset.code, e.message)
            null
        }
    }

    override fun getId(): String = ID

    override fun isMarketSupported(market: Market): Boolean = morningstarConfig.isMarketSupported(market)

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate = morningstarConfig.getMarketDate(market, priceRequest.date, priceRequest.currentMode)

    override fun backFill(asset: Asset): PriceResponse {
        val priceCode = morningstarConfig.getPriceCode(asset)
        val currencyId = asset.market.currency.code

        // Fetch last year of data
        val startDate = LocalDate.now().minusYears(1)
        val json = morningstarProxy.getPrice(priceCode, currencyId, startDate) ?: return PriceResponse(emptyList())

        val prices = parseHistoricalResponse(json, asset)
        return PriceResponse(prices)
    }

    /**
     * Parse all historical prices from the response.
     */
    private fun parseHistoricalResponse(
        json: String,
        asset: Asset
    ): List<MarketData> {
        return try {
            val root = objectMapper.readTree(json)
            val securities = root.path("TimeSeries").path("Security")

            if (securities.isEmpty || !securities.isArray) {
                return emptyList()
            }

            val historyDetail = securities[0].path("HistoryDetail")
            if (historyDetail.isEmpty || !historyDetail.isArray) {
                return emptyList()
            }

            historyDetail.mapNotNull { priceNode ->
                try {
                    val priceDate = LocalDate.parse(priceNode.path("EndDate").asText(), DATE_FORMATTER)
                    val closePrice = BigDecimal(priceNode.path("Value").asText())

                    MarketData(
                        asset = asset,
                        priceDate = priceDate,
                        close = closePrice,
                        source = ID
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Error parsing historical response for {}: {}", asset.code, e.message)
            emptyList()
        }
    }

    override fun isApiSupported(): Boolean = true
}