package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.AMP
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.WtdConfigTest.Companion.CONTRACTS
import com.beancounter.marketdata.providers.wtd.WtdMarketData
import com.beancounter.marketdata.providers.wtd.WtdResponse
import com.beancounter.marketdata.providers.wtd.WtdService
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.set

/**
 * WorldTradingData API tests. This is now a redundant service. It was taken over by MarketStack.com
 *
 * @author mikeh
 * @since 2019-03-04
 */
@SpringBootTest
@ActiveProfiles("wtd")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
internal class WorldTradingDataApiTest {
    private val dateUtils = DateUtils()
    private val marketUtils = PreviousClosePriceDate(dateUtils)
    private val zonedDateTime = LocalDate.now(dateUtils.getZoneId()).atStartOfDay()
    private val priceDate = "2019-11-15"
    @Autowired
    private lateinit var wtdService: WtdService

    @Test
    @Throws(Exception::class)
    fun is_AsxMarketConvertedToAx() {
        val response = getResponseMap(
            ClassPathResource("$CONTRACTS/${AMP.code}-${ASX.code}.json").file
        )
        val assets: MutableCollection<AssetInput> = ArrayList()
        assets.add(getAssetInput(AMP))

        stubFor(
            WireMock.get(
                WireMock.urlEqualTo(
                    "/api/v1/history_multi_single_day?symbol=${AMP.code}.AX&date=$priceDate&api_token=demo"
                )
            )
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(response))
                        .withStatus(200)
                )
        )
        val mdResult = wtdService.getMarketData(
            PriceRequest(priceDate, assets)
        )
        assertThat(mdResult).isNotNull
            .hasSize(1)
    }

    private val testDate = "2019-03-08"

    private val closeField = "close"
    private val assetField = "asset"
    private val openField = "open"

    private val priceDateField = "priceDate"

    @Test
    @Throws(Exception::class)
    fun is_MarketDataDateOverridingRequestDate() {
        val inputs: MutableCollection<AssetInput> = ArrayList()
        inputs.add(getAssetInput(AAPL))
        inputs.add(getAssetInput(MSFT))

        // While the request date is relative to "Today", we are testing that we get back
        //  the date as set in the response from the provider.
        mockWtdResponse(
            inputs, priceDate,
            false, // Prices are at T-1. configured date set in -test.yaml
            ClassPathResource("$CONTRACTS/AAPL-MSFT.json").file
        )
        val mdResult = wtdService.getMarketData(PriceRequest(priceDate, inputs))
        assertThat(mdResult)
            .isNotNull
            .hasSize(2)
        for (marketData in mdResult) {
            if (marketData.asset == MSFT) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue(priceDateField, dateUtils.getDate(testDate))
                    .hasFieldOrPropertyWithValue(assetField, MSFT)
                    .hasFieldOrPropertyWithValue(openField, BigDecimal("109.16"))
                    .hasFieldOrPropertyWithValue(closeField, BigDecimal("110.51"))
            } else if (marketData.asset == AAPL) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue(priceDateField, dateUtils.getDate(testDate))
                    .hasFieldOrPropertyWithValue(assetField, AAPL)
                    .hasFieldOrPropertyWithValue(openField, BigDecimal("170.32"))
                    .hasFieldOrPropertyWithValue(closeField, BigDecimal("172.91"))
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_WtdInvalidAssetPriceDefaulting() {
        val inputs: Collection<AssetInput> = arrayListOf(
            getAssetInput(AAPL),
            getAssetInput(getAsset(NASDAQ.code, "${MSFT.code}x"))
        )

        val nas = Market("NAS", SGD)
        val priceDate = marketUtils.getPriceDate(zonedDateTime, nas).toString()
        mockWtdResponse(inputs, priceDate, false, ClassPathResource("$CONTRACTS/${AAPL.code}.json").file)
        val mdResult = wtdService
            .getMarketData(PriceRequest(assets = inputs))
        assertThat(mdResult)
            .isNotNull
            .hasSize(2)

        // If an invalid asset, then we have a ZERO price
        for (marketData in mdResult) {
            if (marketData.asset == MSFT) {
                assertThat(marketData)
                    .hasFieldOrProperty("date")
                    .hasFieldOrPropertyWithValue(closeField, BigDecimal.ZERO)
            } else if (marketData.asset == AAPL) {
                assertThat(marketData)
                    .hasFieldOrProperty(priceDateField)
                    .hasFieldOrPropertyWithValue(openField, BigDecimal("170.32"))
                    .hasFieldOrPropertyWithValue(closeField, BigDecimal("172.91"))
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_NoDataReturned() {
        val inputs: MutableCollection<AssetInput> = ArrayList()
        inputs.add(getAssetInput(MSFT))
        mockWtdResponse(inputs, priceDate, true, ClassPathResource("$CONTRACTS/NoData.json").file)
        val prices = wtdService.getMarketData(
            PriceRequest(priceDate, inputs)
        )
        assertThat(prices).hasSize(inputs.size)
        assertThat(
            prices.iterator().next()
        ).hasFieldOrPropertyWithValue(closeField, BigDecimal.ZERO)
    }

    companion object {
        private val objectMapper: ObjectMapper = BcJson().objectMapper

        @JvmStatic
        operator fun get(date: String, prices: Map<String, WtdMarketData>): WtdResponse {
            return WtdResponse(date, prices)
        }

        @JvmStatic
        operator fun get(
            date: String,
            asset: Asset,
            open: String?,
            close: String?,
            high: String?,
            low: String?,
            volume: String?,
        ): MarketData {
            val result = MarketData(asset, DateUtils().getDate(date))
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
            assets: Collection<AssetInput>,
            asAt: String?,
            overrideAsAt: Boolean,
            jsonFile: File?,
        ) {
            var assetArg: StringBuilder? = null
            for ((_, _, _, asset) in assets) {
                assertThat(asset).isNotNull
                assertThat(asset!!.market).isNotNull
                val market = asset.market
                var suffix = ""
                if (!market.code.equals("NASDAQ", ignoreCase = true)) {
                    // Horrible hack to support WTD contract mocking ASX/AX
                    suffix = "." + if (market.code.equals(ASX.code, ignoreCase = true)) "AX" else market.code
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
            stubFor(
                WireMock.get(
                    WireMock.urlEqualTo(
                        "/api/v1/history_multi_single_day?symbol=" + assetArg +
                            "&date=" + asAt +
                            "&api_token=demo"
                    )
                )
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(response))
                            .withStatus(200)
                    )
            )
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
}
