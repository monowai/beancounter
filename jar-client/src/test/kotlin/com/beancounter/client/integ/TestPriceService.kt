package com.beancounter.client.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test market data prices from the client contract perspective.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:10990"]
)
@ImportAutoConfiguration(
    ClientConfig::class
)
@SpringBootTest(classes = [ClientConfig::class])
@ActiveProfiles("jar-client-shared", "contract-base")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestPriceService {
    @Autowired
    private lateinit var priceService: PriceService

    @MockitoBean
    private lateinit var assetIngestService: AssetIngestService

    @MockitoBean
    private lateinit var tokenService: TokenService

    @Test
    fun is_TodaysPriceForEbayFound() {
        assertThat(assetIngestService).isNotNull
        val priceRequest =
            PriceRequest(
                "2019-10-18",
                currentMode = true, // Hack to make contract testing easier
                assets =
                    listOf(
                        PriceAsset(
                            "NASDAQ",
                            "EBAY",
                            assetId = "EBAY"
                        )
                    )
            )
        `when`(tokenService.bearerToken).thenReturn("")
        val response = priceService.getPrices(priceRequest)

        assertThat(response).isNotNull
        assertThat(response.data).isNotNull.hasSize(1)

        val marketData = response.data.iterator().next()
        assertThat(marketData.asset.market).isNotNull
        assertThat(marketData)
            .hasFieldOrProperty("close")
            .hasFieldOrProperty("open")
            .hasFieldOrProperty("high")
            .hasFieldOrProperty("low")
            .hasFieldOrProperty("priceDate")
    }

    @Test
    fun is_EventsFound() {
        val response = priceService.getEvents("NDAQ")
        assertThat(response)
            .isNotNull
            .hasFieldOrProperty("data")

        for (datum in response.data) {
            assertThat(isSplit(datum) || isDividend(datum))
        }
    }
}