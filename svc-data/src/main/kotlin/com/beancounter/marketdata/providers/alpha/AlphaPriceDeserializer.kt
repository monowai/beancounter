package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.get
import com.beancounter.common.utils.PercentUtils
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Objects

private const val SPLIT = "8. split coefficient"

private const val DIVI = "7. dividend amount"

private const val VOLUME = "6. volume"

const val F_OPEN = "02. open"

const val F_HIGH = "03. high"

const val F_LOW = "04. low"

const val F_PRICE = "05. price"

const val F_VOLUME = "06. volume"

const val F_PREVIOUS_CLOSE = "08. previous close"

const val F_CHANGE = "09. change"

const val F_DATE = "07. latest trading day"

/**
 * Deserialize various AlphaVantage responses to a normalised PriceResponse.
 *
 * @author mikeh
 * @since 2019-03-03
 */
class AlphaPriceDeserializer : JsonDeserializer<PriceResponse>() {
    private val dateUtils = DateUtils()
    private val percentUtils = PercentUtils()

    override fun deserialize(
        p: JsonParser,
        ctx: DeserializationContext
    ): PriceResponse {
        val source = p.codec.readTree<JsonNode>(p)

        return when {
            source.has(TIME_SERIES_DAILY) -> handleTimeSeries(source)
            source.has(GLOBAL_QUOTE) -> handleGlobal(source)
            source.has("Information") || source.has("Note") || source.has("Error Message") -> {
                // AlphaVantage error response (rate limit, invalid key, etc.)
                // Return empty response - let caller handle gracefully
                log.warn("AlphaVantage API error: {}", source.toString().take(ERROR_MESSAGE_MAX_LENGTH))
                PriceResponse(emptyList())
            }
            else -> throw BusinessException(
                "Unable to handle AlphaVantage response: ${source.toString().take(ERROR_MESSAGE_MAX_LENGTH)}"
            )
        }
    }

    companion object {
        const val GLOBAL_QUOTE = "Global Quote"
        const val TIME_SERIES_DAILY = "Time Series (Daily)"
        private const val ERROR_MESSAGE_MAX_LENGTH = 200
        private val mapper = objectMapper
        private val log = org.slf4j.LoggerFactory.getLogger(AlphaPriceDeserializer::class.java)
    }

    private fun handleGlobal(source: JsonNode): PriceResponse {
        val metaData = source["Global Quote"]
        val asset = getAsset(metaData, "01. symbol")
        val mapType =
            mapper.typeFactory.constructMapType(LinkedHashMap::class.java, String::class.java, String::class.java)
        val data = mapper.readValue<Map<String, Map<String, String>>>(metaData.toString(), mapType)

        return getMdFromGlobal(asset, data)
    }

    private fun getMdFromGlobal(
        asset: Asset?,
        data: Map<String, Any>
    ): PriceResponse {
        val results = mutableListOf<MarketData>()
        if (asset != null) {
            val priceDate = data[F_DATE].toString()
            val change = get(data[F_CHANGE].toString()) ?: BigDecimal.ZERO
            val previousClose = get(data[F_PREVIOUS_CLOSE].toString()) ?: BigDecimal.ZERO
            val changePercent = percentUtils.percent(change, previousClose)

            val price =
                MarketData(
                    asset = asset,
                    priceDate = dateUtils.getFormattedDate(priceDate),
                    close = BigDecimal(data[F_PRICE].toString()),
                    open = BigDecimal(data[F_OPEN].toString()),
                    low = BigDecimal(data[F_LOW].toString()),
                    high = BigDecimal(data[F_HIGH].toString()),
                    previousClose = previousClose,
                    change = change,
                    changePercent = changePercent,
                    volume = Integer.decode(data[F_VOLUME].toString()),
                    source = "ALPHA"
                )
            results.add(price)
        }
        return PriceResponse(results)
    }

    private fun handleTimeSeries(source: JsonNode): PriceResponse {
        val results: MutableCollection<MarketData> = ArrayList()
        val metaData = source["Meta Data"]
        val asset = getAsset(metaData, "2. Symbol")
        val mapType =
            mapper.typeFactory
                .constructMapType(LinkedHashMap::class.java, String::class.java, HashMap::class.java)
        val allValues =
            mapper.readValue<LinkedHashMap<*, out LinkedHashMap<String, Any>?>>(
                source["Time Series (Daily)"].toString(),
                mapType
            )
        for (key in allValues.keys) {
            val rawData: Map<String, Any>? = allValues[key.toString()]
            val localDateTime =
                dateUtils.getFormattedDate(
                    key.toString()
                )
            if (asset != null) {
                val priceData = getPrice(asset, localDateTime, rawData!!)
                results.add(priceData)
            }
        }
        return PriceResponse(results)
    }

    private fun getPrice(
        asset: Asset,
        priceDate: LocalDate = LocalDate.now(),
        data: Map<String, Any> = mapOf()
    ): MarketData {
        val price: MarketData?
        try {
            price =
                MarketData(
                    asset = asset,
                    priceDate = Objects.requireNonNull(priceDate)!!,
                    close = BigDecimal(data["4. close"].toString()),
                    open = BigDecimal(data["1. open"].toString()),
                    low = BigDecimal(data["3. low"].toString()),
                    high = BigDecimal(data["2. high"].toString()),
                    volume = if (data[VOLUME] != null) BigDecimal(data[VOLUME].toString()).intValueExact() else 0,
                    dividend = if (data[DIVI] != null) BigDecimal(data[DIVI].toString()) else BigDecimal.ZERO,
                    split = if (data[SPLIT] != null) BigDecimal(data[SPLIT].toString()) else BigDecimal.ONE
                )
        } catch (_: NumberFormatException) {
            // oops
            return MarketData(asset = asset, priceDate = priceDate)
        }
        return price
    }

    private fun getAsset(
        nodeValue: JsonNode,
        assetField: String
    ): Asset? {
        if (!isNull(nodeValue)) {
            val symbols =
                nodeValue[assetField] ?: return null

            val values = symbols.asText().split(":").toTypedArray()
            var market = Market("US")
            if (values.size > 1) {
                // We have a market
                market = Market(values[1])
            }
            return Asset(code = values[0], market = market)
        }
        throw BusinessException("Unable to resolve asset ${nodeValue.asText()}")
    }

    private fun isNull(nodeValue: JsonNode) = nodeValue.isNull || nodeValue.asText() == "null"
}