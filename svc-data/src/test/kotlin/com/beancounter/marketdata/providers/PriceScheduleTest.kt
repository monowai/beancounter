package com.beancounter.marketdata.providers

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * PriceScheduler bean deploys when enabled and functionality works
 */
@SpringBootTest(classes = [MarketDataBoot::class], properties = ["schedule.enabled=true"])
@Tag("slow")
@AutoConfigureMockAuth
class PriceScheduleTest {
    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var assetService: AssetService
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var priceSchedule: PriceSchedule

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var dateUtils: DateUtils

    private lateinit var token: Jwt

    @Test
    fun is_PriceUpdated() {
        val code = "AMP"
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()

        marketDataService.purge()
        assetService.purge()
        val assetResult = assetService.findOrCreate(AssetInput(ASX.code, code = code))
        val asset = assetResult
        priceSchedule.updatePrices()
        Thread.sleep(2000) // Async reads/writes
        val price = marketDataService.getPriceResponse(PriceRequest.of(AssetInput(asset.market.code, asset.code)))
        Assertions.assertThat(price).hasNoNullFieldsOrProperties()
    }
}
