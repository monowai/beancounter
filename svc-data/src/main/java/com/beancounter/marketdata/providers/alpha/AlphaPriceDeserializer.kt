package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.get
import com.beancounter.common.utils.PercentUtils
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Objects

/**
 * Deserialize various AlphaVantage responses to a normalised PriceResponse.
 *
 * @author mikeh
 * @since 2019-03-03
 */
class AlphaPriceDeserializer : JsonDeserializer<PriceResponse?>() {
    private val dateUtils = DateUtils()
    private val percentUtils = PercentUtils()

    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): PriceResponse? {
        val source = p.codec.readTree<JsonNode>(p)
        if (source.has(TIME_SERIES_DAILY)) {
            return handleTimeSeries(source)
        } else if (source.has(GLOBAL_QUOTE)) {
            return handleGlobal(source)
            // nodeValue = source.get("Global Quote");
            // marketData =getMarketData(asset, )
        }
        return null
    }

    @Throws(JsonProcessingException::class)
    private fun handleGlobal(source: JsonNode): PriceResponse {
        val metaData = source["Global Quote"]
        val asset = getAsset(metaData, "01. symbol")
        val mapType = mapper.typeFactory
            .constructMapType(LinkedHashMap::class.java, String::class.java, String::class.java)
        return getMdFromGlobal(asset, mapper.readValue<Map<String, Any>>(metaData.toString(), mapType))
    }

    private fun getMdFromGlobal(asset: Asset?, data: Map<String, Any>?): PriceResponse {
        val results: MutableCollection<MarketData> = ArrayList()
        if (data != null && asset != null) {
            val open = BigDecimal(data["02. open"].toString())
            val high = BigDecimal(data["03. high"].toString())
            val low = BigDecimal(data["04. low"].toString())
            val close = BigDecimal(data["05. price"].toString())
            val volume = Integer.decode(data["06. volume"].toString())
            val priceDate = data["07. latest trading day"].toString()
            val previousClose = get(data["08. previous close"].toString())
            val change = get(data["09. change"].toString())
            val price = MarketData(asset, Objects.requireNonNull(dateUtils.getDate(priceDate))!!)
            price.open = open
            price.close = close
            price.high = high
            price.low = low
            price.volume = volume
            price.previousClose = previousClose
            price.change = change
            price.changePercent = percentUtils.percent(change, previousClose)
            results.add(price)
        }
        return PriceResponse(results)
    }

    @Throws(JsonProcessingException::class)
    private fun handleTimeSeries(source: JsonNode): PriceResponse {
        val results: MutableCollection<MarketData> = ArrayList()
        val metaData = source["Meta Data"]
        val asset = getAsset(metaData, "2. Symbol")
        if (asset != null) {
            val mapType = mapper.typeFactory
                .constructMapType(LinkedHashMap::class.java, String::class.java, HashMap::class.java)
            val allValues = mapper.readValue<LinkedHashMap<*, out LinkedHashMap<String, Any>?>>(source["Time Series (Daily)"].toString(), mapType)
            for (key in allValues.keys) {
                val rawData: Map<String, Any>? = allValues[key.toString()]
                val localDateTime = dateUtils.getLocalDate(
                    key.toString(), "yyyy-M-dd"
                )
                val priceData = getPrice(asset, localDateTime, rawData)
                if (priceData != null) {
                    results.add(priceData)
                }
            }
        }
        return PriceResponse(results)
    }

    private fun getPrice(asset: Asset, priceDate: LocalDate?, data: Map<String, Any>?): MarketData? {
        var price: MarketData? = null
        if (data != null) {
            price = MarketData(asset, Objects.requireNonNull(priceDate)!!)
            price.open = BigDecimal(data["1. open"].toString())
            price.close = BigDecimal(data["4. close"].toString())
            price.high = BigDecimal(data["2. high"].toString())
            price.low = BigDecimal(data["3. low"].toString())
            price.volume = BigDecimal(data["6. volume"].toString()).intValueExact()
            price.split = BigDecimal(data["8. split coefficient"].toString())
            price.dividend = BigDecimal(data["7. dividend amount"].toString())
        }
        return price
    }

    private fun getAsset(nodeValue: JsonNode, assetField: String): Asset? {
        var asset: Asset? = null
        if (!isNull(nodeValue)) {
            val symbols = nodeValue[assetField] ?: return null

            val values = symbols.asText().split(":").toTypedArray()
            var market = Market("US", Currency("USD"))
            if (values.size > 1) {
                // We have a market
                market = Market(values[1], Currency("USD"))
            }
            asset = Asset(values[0], market)
        }
        return asset
    }

    private fun isNull(nodeValue: JsonNode?): Boolean {
        return nodeValue == null || nodeValue.isNull || nodeValue.asText() == "null"
    }

    companion object {
        const val GLOBAL_QUOTE = "Global Quote"
        const val TIME_SERIES_DAILY = "Time Series (Daily)"
        private val mapper = BcJson().objectMapper
    }
}
