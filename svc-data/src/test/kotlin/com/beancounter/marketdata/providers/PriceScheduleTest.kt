package com.beancounter.marketdata.providers

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * PriceScheduler bean deploys when enabled and functionality works
 */
@SpringBootTest(properties = ["schedule.enabled=true"])
@SpringMvcDbTest
class PriceScheduleTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var enrichmentFactory: EnrichmentFactory

    @MockitoBean
    private lateinit var alphaPriceService: AlphaPriceService

    @MockitoBean
    private lateinit var cashService: CashService

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var priceSchedule: PriceSchedule

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt

    private lateinit var mockMvc: MockMvc

    @Test
    fun is_PriceUpdated() {
        val code = "AMPX"
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        marketDataService.purge()
        val asxMarket =
            marketService.getMarket(
                ASX.code,
                false
            )
        Mockito
            .`when`(
                enrichmentFactory.getEnricher(asxMarket)
            ).thenReturn(defaultEnricher)

        val assetInput =
            AssetInput(
                ASX.code,
                code = code
            )
        val assetResult = assetService.findOrCreate(assetInput)
        Mockito
            .`when`(
                alphaPriceService.getMarketData(
                    PriceRequest(
                        anyString(),
                        listOf(PriceAsset(assetResult))
                    )
                )
            ).thenReturn(setOf(MarketData(assetResult)))
        priceSchedule.updatePrices()
        Thread.sleep(2000) // Async reads/writes
        val price =
            marketDataService.getPriceResponse(
                PriceRequest.of(
                    AssetInput(
                        assetResult.market.code,
                        assetResult.code
                    )
                )
            )
        assertThat(price).hasNoNullFieldsOrProperties()
    }
}