package com.beancounter.marketdata.providers.marketstack

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.AMP
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.providers.marketstack.MarketStackResponseTest.Companion.CONTRACTS
import com.beancounter.marketdata.providers.marketstack.MarketStackService.Companion.ID
import com.beancounter.marketdata.providers.marketstack.model.MarketStackData
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import java.io.IOException
import java.math.BigDecimal

/**
 * MarketStack API tests using WireMock.
 *
 * @author mikeh
 * @since 2019-03-04
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db", "mstack")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
internal class MarketStackApiTest {
    private val dateUtils = DateUtils()
    private val priceDate = "2019-11-15"

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var marketStackService: MarketStackService

    private val testDate = "2024-11-29"

    private val closeField = "close"
    private val assetField = "asset"
    private val openField = "open"
    private val sourceField = "source"

    private val priceDateField = "priceDate"

    @Test
    fun `returns prices based when we override the requested date`() {
        val inputs =
            mutableListOf(
                PriceAsset(AAPL),
                PriceAsset(MSFT)
            )

        // While the request date is relative to "Today", we are testing that we get back
        //  the date as set in the response from the provider.
        mockResponse(
            inputs,
            priceDate,
            false,
            ClassPathResource("$CONTRACTS/${AAPL.code}-${MSFT.code}.json").file
        )
        val mdResult =
            marketStackService.getMarketData(
                PriceRequest(
                    priceDate,
                    inputs
                )
            )
        assertThat(mdResult)
            .isNotNull
            .hasSize(2)
        mdResult.forEach { marketData ->
            if (marketData.asset == MSFT) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue(
                        priceDateField,
                        dateUtils.getFormattedDate(testDate)
                    ).hasFieldOrPropertyWithValue(
                        assetField,
                        MSFT
                    ).hasFieldOrPropertyWithValue(
                        openField,
                        BigDecimal("420.09")
                    ).hasFieldOrPropertyWithValue(
                        closeField,
                        BigDecimal("423.46")
                    )
            } else if (marketData.asset == AAPL) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue(
                        priceDateField,
                        dateUtils.getFormattedDate(testDate)
                    ).hasFieldOrPropertyWithValue(
                        assetField,
                        AAPL
                    ).hasFieldOrPropertyWithValue(
                        openField,
                        BigDecimal("234.81")
                    ).hasFieldOrPropertyWithValue(
                        closeField,
                        BigDecimal("237.33")
                    )
            }
        }
    }

    @Test
    fun `returns a price of zero when a price does not exist on the day`() {
        val assets =
            listOf(
                PriceAsset(AAPL),
                PriceAsset(MSFT)
            )

        val utcToday = dateUtils.offsetDateString()

        mockResponse(
            assets,
            utcToday,
            false,
            ClassPathResource("$CONTRACTS/${AAPL.code}.json").file
        )
        val mdResult =
            marketStackService.getMarketData(PriceRequest(assets = assets))
        assertThat(mdResult)
            .isNotNull
            .hasSize(assets.size)

        // If an invalid asset, then we have a ZERO price
        mdResult.forEach { marketData ->
            when (marketData.asset) {
                MSFT ->
                    assertThat(marketData)
                        .hasFieldOrProperty(priceDateField)
                        .hasFieldOrPropertyWithValue(
                            closeField,
                            BigDecimal.ZERO
                        ).hasFieldOrPropertyWithValue(
                            sourceField,
                            ID
                        )

                AAPL ->
                    assertThat(marketData)
                        .hasFieldOrProperty(priceDateField)
                        .hasFieldOrPropertyWithValue(
                            openField,
                            BigDecimal("234.81")
                        ).hasFieldOrPropertyWithValue(
                            closeField,
                            BigDecimal("237.33")
                        ).hasFieldOrPropertyWithValue(
                            sourceField,
                            ID
                        )
            }
        }
    }

    @Test
    fun `no data returns`() {
        val inputs = listOf(PriceAsset(MSFT))
        mockResponse(
            inputs,
            priceDate,
            true,
            ClassPathResource("$CONTRACTS/no-data.json").file
        )
        val prices =
            marketStackService.getMarketData(
                PriceRequest(
                    priceDate,
                    inputs
                )
            )
        assertThat(prices).hasSize(inputs.size)
        assertThat(
            prices.first()
        ).hasFieldOrPropertyWithValue(
            closeField,
            BigDecimal.ZERO
        )
    }

    @Test
    fun `asx market is correctly resolved and appended so Asset Price returns`() {
        val inputs = listOf(PriceAsset(AMP))
        mockResponse(
            inputs,
            priceDate,
            true,
            ClassPathResource("$CONTRACTS/${AMP.code}-${ASX.code}.json").file
        )
        val prices =
            marketStackService.getMarketData(
                PriceRequest(
                    priceDate,
                    inputs
                )
            )
        assertThat(prices).hasSize(inputs.size)
        assertThat(
            prices.first()
        ).hasFieldOrPropertyWithValue(
            closeField,
            BigDecimal("1.335")
        )
    }

    /**
     * Constants for MarketStack.
     **/
    companion object {
        @JvmStatic
        operator fun get(prices: List<MarketStackData>): MarketStackResponse = MarketStackResponse(data = prices)

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
        fun mockResponse(
            assets: Collection<PriceAsset>,
            asAt: String,
            overrideAsAt: Boolean,
            jsonFile: File
        ) {
            var assetArg: StringBuilder? = null
            for ((_, _, resolvedAsset) in assets) {
                assertThat(resolvedAsset).isNotNull
                assertThat(resolvedAsset!!.market).isNotNull
                val market = resolvedAsset.market
                var suffix = ""
                if (!market.code.equals(
                        NASDAQ.code,
                        ignoreCase = true
                    )
                ) {
                    // Horrible hack to support MarketStack contract mocking ASX/AX
                    suffix = "." +
                        if (market.code.equals(
                                ASX.code,
                                ignoreCase = true
                            )
                        ) {
                            "XASX"
                        } else {
                            market.code
                        }
                }
                if (assetArg != null) {
                    assetArg.append("%2C").append(resolvedAsset.code).append(suffix)
                } else {
                    assetArg = StringBuilder(resolvedAsset.code).append(suffix)
                }
            }
            val response = getResponseMap(jsonFile)
            if (overrideAsAt) {
                response["date"] = asAt
            }
            stubFor(
                WireMock
                    .get(
                        WireMock.urlEqualTo(
                            "/v1/eod/$asAt?symbols=$assetArg&access_key=demo"
                        )
                    ).willReturn(
                        WireMock
                            .aResponse()
                            .withHeader(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE
                            ).withBody(objectMapper.writeValueAsString(response))
                            .withStatus(200)
                    )
            )
        }

        /**
         * Helper to return a Map from a JSON file.
         *
         * @param jsonFile input file
         * @return MutableMap
         * @throws IOException err
         */
        @JvmStatic
        fun getResponseMap(jsonFile: File?): MutableMap<String, Any> {
            val mapType =
                objectMapper.typeFactory
                    .constructMapType(
                        LinkedHashMap::class.java,
                        String::class.java,
                        Any::class.java
                    )
            return objectMapper.readValue(
                jsonFile,
                mapType
            )
        }
    }
}