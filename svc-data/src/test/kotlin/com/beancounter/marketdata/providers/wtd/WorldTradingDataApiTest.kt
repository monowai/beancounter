package com.beancounter.marketdata.providers.wtd

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.AMP
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.providers.wtd.WtdConfigTest.Companion.CONTRACTS
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
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
import kotlin.collections.set

/**
 * WorldTradingData API tests. This is now a redundant service. It was taken over by MarketStack.com
 *
 * @author mikeh
 * @since 2019-03-04
 */
@SpringBootTest
@ActiveProfiles("wtd")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
@Disabled
internal class WorldTradingDataApiTest {
    private val dateUtils = DateUtils()
    private val priceDate = "2019-11-15"

    @Autowired
    private lateinit var wtdService: WtdService

    @Test
    fun is_AsxMarketConvertedToAx() {
        val response =
            getResponseMap(
                ClassPathResource("$CONTRACTS/${AMP.code}-${ASX.code}.json").file,
            )
        val assets = mutableListOf(PriceAsset(AMP))

        stubFor(
            WireMock
                .get(
                    WireMock.urlEqualTo(
                        "/api/v1/history_multi_single_day?symbol=${AMP.code}.AX&date=$priceDate&api_token=demo",
                    ),
                ).willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(response))
                        .withStatus(200),
                ),
        )
        val mdResult =
            wtdService.getMarketData(
                PriceRequest(priceDate, assets),
            )
        assertThat(mdResult)
            .isNotNull
            .hasSize(1)
    }

    private val testDate = "2019-03-08"

    private val closeField = "close"
    private val assetField = "asset"
    private val openField = "open"

    private val priceDateField = "priceDate"

    @Test
    fun is_MarketDataDateOverridingRequestDate() {
        val inputs = mutableListOf(PriceAsset(AAPL), PriceAsset(MSFT))

        // While the request date is relative to "Today", we are testing that we get back
        //  the date as set in the response from the provider.
        mockWtdResponse(
            inputs,
            priceDate,
            false,
            ClassPathResource("$CONTRACTS/AAPL-MSFT.json").file,
        )
        val mdResult = wtdService.getMarketData(PriceRequest(priceDate, inputs))
        assertThat(mdResult)
            .isNotNull
            .hasSize(2)
        for (marketData in mdResult) {
            if (marketData.asset == MSFT) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue(priceDateField, dateUtils.getFormattedDate(testDate))
                    .hasFieldOrPropertyWithValue(assetField, MSFT)
                    .hasFieldOrPropertyWithValue(openField, BigDecimal("109.16"))
                    .hasFieldOrPropertyWithValue(closeField, BigDecimal("110.51"))
            } else if (marketData.asset == AAPL) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue(priceDateField, dateUtils.getFormattedDate(testDate))
                    .hasFieldOrPropertyWithValue(assetField, AAPL)
                    .hasFieldOrPropertyWithValue(openField, BigDecimal("170.32"))
                    .hasFieldOrPropertyWithValue(closeField, BigDecimal("172.91"))
            }
        }
    }

    @Test
    fun is_WtdInvalidAssetPriceDefaulting() {
        val inputs =
            listOf(
                PriceAsset(AAPL),
                PriceAsset(getTestAsset(NASDAQ, "${MSFT.code}x")),
            )

        val utcToday = dateUtils.offsetDateString()

        mockWtdResponse(
            inputs,
            utcToday,
            false,
            ClassPathResource("$CONTRACTS/${AAPL.code}.json").file,
        )
        val mdResult =
            wtdService
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
    fun is_NoDataReturned() {
        val inputs = listOf(PriceAsset(MSFT))
        mockWtdResponse(inputs, priceDate, true, ClassPathResource("$CONTRACTS/NoData.json").file)
        val prices =
            wtdService.getMarketData(
                PriceRequest(priceDate, inputs),
            )
        assertThat(prices).hasSize(inputs.size)
        assertThat(
            prices.iterator().next(),
        ).hasFieldOrPropertyWithValue(closeField, BigDecimal.ZERO)
    }

    companion object {
        @JvmStatic
        operator fun get(
            date: String,
            prices: Map<String, WtdMarketData>,
        ): WtdResponse = WtdResponse(date, prices)

        /**
         * Mock the WTD response.
         *
         * @param assets       Assets for which an argument will be created from
         * @param asAt         Date that will be requested in the response
         * @param overrideAsAt if true, then asAt will override the mocked priceDate
         * @param jsonFile     Read response from this file.
         * @throws IOException error
         */
        @JvmStatic
        fun mockWtdResponse(
            assets: Collection<PriceAsset>,
            asAt: String?,
            overrideAsAt: Boolean,
            jsonFile: File?,
        ) {
            var assetArg: StringBuilder? = null
            for ((_, _, asset) in assets) {
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
                WireMock
                    .get(
                        WireMock.urlEqualTo(
                            "/api/v1/history_multi_single_day?symbol=" + assetArg +
                                "&date=" + asAt +
                                "&api_token=demo",
                        ),
                    ).willReturn(
                        WireMock
                            .aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(response))
                            .withStatus(200),
                    ),
            )
        }

        /**
         * Helper to return a Map from a JSON file.
         *
         * @param jsonFile input file
         * @return Map
         * @throws IOException err
         */
        @JvmStatic
        fun getResponseMap(jsonFile: File?): HashMap<String, Any> {
            val mapType =
                objectMapper.typeFactory
                    .constructMapType(LinkedHashMap::class.java, String::class.java, Any::class.java)
            return objectMapper.readValue(jsonFile, mapType)
        }
    }
}
