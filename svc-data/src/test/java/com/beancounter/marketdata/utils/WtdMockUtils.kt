package com.beancounter.marketdata.utils

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MarketUtils
import com.beancounter.marketdata.providers.wtd.WtdMarketData
import com.beancounter.marketdata.providers.wtd.WtdResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.assertj.core.api.Assertions
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * WorldTradingData mocking support.
 */
object WtdMockUtils {
    const val WTD_PATH = "/contracts/wtd"
    private val objectMapper: ObjectMapper = BcJson().objectMapper
    private val dateUtils = DateUtils()
    private val marketUtils = MarketUtils(dateUtils)
    private val zonedDateTime = LocalDate.now(dateUtils.getZoneId()).atStartOfDay()

    @JvmStatic
    private val wtdApi = WireMockRule(WireMockConfiguration.options().port(8888))

    @JvmStatic
    fun getWtdApi(): WireMockRule {
        if (!wtdApi.isRunning) {
            this.wtdApi.start()
        }
        return this.wtdApi
    }

    @JvmStatic
    val sgx = Market("SGX", Currency("SGD"), ZoneId.systemDefault().id)

    @JvmStatic
    val priceDate = marketUtils.getLastMarketDate(zonedDateTime, sgx).toString()

    @JvmStatic
    operator fun get(date: String, prices: Map<String, WtdMarketData>): WtdResponse {
        return WtdResponse(date, prices)
    }

    @JvmStatic
    operator fun get(date: String?, asset: Asset?, open: String?,
                     close: String?, high: String?, low: String?, volume: String?): MarketData {
        val result = MarketData(asset!!, Objects.requireNonNull(dateUtils.getDate(date))!!)
        result.open = BigDecimal(open)
        result.close = BigDecimal(close)
        result.high = BigDecimal(high)
        result.low = BigDecimal(low)
        result.volume = Integer.decode(volume)
        return result
    }

    /**
     * Mock the WTD response.
     *
     * @param assets       Assets for which an argument will be created from
     * @param asAt         Date that will be requested in the response
     * @param overrideAsAt if true, then asAt will override the mocked priceDate
     * @param jsonFile     Read response from this file.
     * @throws IOException error
     */
    @Throws(IOException::class)
    @JvmStatic
    fun mockWtdResponse(
            assets: Collection<AssetInput>, asAt: String?, overrideAsAt: Boolean, jsonFile: File?) {
        var assetArg: StringBuilder? = null
        for ((_, _, _, asset) in assets) {
            Assertions.assertThat(asset).isNotNull
            Assertions.assertThat(asset!!.market).isNotNull
            val market = asset.market
            var suffix = ""
            if (!market.code.equals("NASDAQ", ignoreCase = true)) {
                // Horrible hack to support WTD contract mocking ASX/AX
                suffix = "." + if (market.code.equals("ASX", ignoreCase = true)) "AX" else market.code
            }
            if (assetArg != null) {
                assetArg.append("%2C").append(asset.code).append(suffix)
            } else {
                assetArg = StringBuilder(asset.code).append(suffix)
            }
        }
        val response = getResponseMap(jsonFile)
        if (asAt != null && overrideAsAt) {
            response["date"] = asAt
        }
        this.wtdApi.stubFor(
                WireMock.get(WireMock.urlEqualTo(
                        "/api/v1/history_multi_single_day?symbol=" + assetArg
                                + "&date=" + asAt
                                + "&api_token=demo"))
                        .willReturn(
                            WireMock.aResponse()
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(objectMapper.writeValueAsString(response))
                                .withStatus(200)))
    }

    /**
     * Helper to return a Map from a JSON file.
     *
     * @param jsonFile input file
     * @return Map
     * @throws IOException err
     */
    @Throws(IOException::class)
    @JvmStatic
    fun getResponseMap(jsonFile: File?): HashMap<String, Any> {
        val mapType = objectMapper.typeFactory
            .constructMapType(LinkedHashMap::class.java, String::class.java, Any::class.java)
        return objectMapper.readValue(jsonFile, mapType)
    }
}