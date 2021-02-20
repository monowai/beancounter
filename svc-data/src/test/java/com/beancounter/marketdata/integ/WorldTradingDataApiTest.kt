package com.beancounter.marketdata.integ

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.contracts.ContractVerifierBase
import com.beancounter.marketdata.providers.wtd.WtdService
import com.beancounter.marketdata.utils.WtdMockUtils
import com.beancounter.marketdata.utils.WtdMockUtils.getResponseMap
import com.beancounter.marketdata.utils.WtdMockUtils.mockWtdResponse
import com.beancounter.marketdata.utils.WtdMockUtils.priceDate
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.math.BigDecimal
import java.util.ArrayList

/**
 * WorldTradingData API tests.
 *
 * @author mikeh
 * @since 2019-03-04
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("wtd")
internal class WorldTradingDataApiTest {
    private val dateUtils = DateUtils()

    @Autowired
    private lateinit var wtdService: WtdService
    private val objectMapper: ObjectMapper = BcJson().objectMapper
    companion object {
        val api = WtdMockUtils.getWtdApi()

        @BeforeAll
        @JvmStatic
        fun is_ApiRunning() {
            assertThat(api).isNotNull
            assertThat(api.isRunning).isTrue()
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_AsxMarketConvertedToAx() {
        val response = getResponseMap(
            ClassPathResource(WtdMockUtils.WTD_PATH + "/AMP-ASX.json").file
        )
        val assets: MutableCollection<AssetInput> = ArrayList()
        assets.add(getAssetInput(ContractVerifierBase.AMP))
        api.stubFor(
            WireMock.get(
                WireMock.urlEqualTo(
                    "/api/v1/history_multi_single_day?symbol=AMP.AX&date=" +
                        "2019-11-15" +
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
        val mdResult = wtdService.getMarketData(
            PriceRequest("2019-11-15", assets)
        )
        assertThat(mdResult).isNotNull
            .hasSize(1)
    }

    @Test
    @Throws(Exception::class)
    fun is_MarketDataDateOverridingRequestDate() {
        val inputs: MutableCollection<AssetInput> = ArrayList()
        inputs.add(getAssetInput(ContractVerifierBase.AAPL))
        inputs.add(getAssetInput(ContractVerifierBase.MSFT))

        // While the request date is relative to "Today", we are testing that we get back
        //  the date as set in the response from the provider.
        mockWtdResponse(
            inputs, "2019-11-15",
            false, // Prices are at T-1. configured date set in -test.yaml
            ClassPathResource(WtdMockUtils.WTD_PATH + "/AAPL-MSFT.json").file
        )
        val mdResult = wtdService.getMarketData(PriceRequest("2019-11-15", inputs))
        assertThat(mdResult)
            .isNotNull
            .hasSize(2)
        for (marketData in mdResult) {
            if (marketData.asset == ContractVerifierBase.MSFT) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue("priceDate", dateUtils.getDate("2019-03-08"))
                    .hasFieldOrPropertyWithValue("asset", ContractVerifierBase.MSFT)
                    .hasFieldOrPropertyWithValue("open", BigDecimal("109.16"))
                    .hasFieldOrPropertyWithValue("close", BigDecimal("110.51"))
            } else if (marketData.asset == ContractVerifierBase.AAPL) {
                assertThat(marketData)
                    .hasFieldOrPropertyWithValue("priceDate", dateUtils.getDate("2019-03-08"))
                    .hasFieldOrPropertyWithValue("asset", ContractVerifierBase.AAPL)
                    .hasFieldOrPropertyWithValue("open", BigDecimal("170.32"))
                    .hasFieldOrPropertyWithValue("close", BigDecimal("172.91"))
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_WtdInvalidAssetPriceDefaulting() {
        val inputs: MutableCollection<AssetInput> = ArrayList()
        inputs.add(getAssetInput(ContractVerifierBase.AAPL))
        inputs.add(getAssetInput(ContractVerifierBase.MSFT_INVALID))

        // Prices are at T-1. configured date set in -test.yaml
        mockWtdResponse(inputs, priceDate, true, ClassPathResource(WtdMockUtils.WTD_PATH + "/APPL.json").file)
        val mdResult = wtdService
            .getMarketData(PriceRequest(inputs))
        assertThat(mdResult)
            .isNotNull
            .hasSize(2)

        // If an invalid asset, then we have a ZERO price
        for (marketData in mdResult) {
            if (marketData.asset == ContractVerifierBase.MSFT) {
                assertThat(marketData)
                    .hasFieldOrProperty("date")
                    .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO)
            } else if (marketData.asset == ContractVerifierBase.AAPL) {
                assertThat(marketData)
                    .hasFieldOrProperty("priceDate")
                    .hasFieldOrPropertyWithValue("open", BigDecimal("170.32"))
                    .hasFieldOrPropertyWithValue("close", BigDecimal("172.91"))
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_NoDataReturned() {
        val inputs: MutableCollection<AssetInput> = ArrayList()
        inputs.add(getAssetInput(ContractVerifierBase.MSFT))
        mockWtdResponse(inputs, "2019-11-15", true, ClassPathResource(WtdMockUtils.WTD_PATH + "/NoData.json").file)
        val prices = wtdService.getMarketData(
            PriceRequest("2019-11-15", inputs)
        )
        assertThat(prices).hasSize(inputs.size)
        assertThat(
            prices.iterator().next()
        ).hasFieldOrPropertyWithValue("close", BigDecimal.ZERO)
    }
}
