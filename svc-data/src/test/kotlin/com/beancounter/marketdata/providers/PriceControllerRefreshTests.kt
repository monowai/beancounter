package com.beancounter.marketdata.providers

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * MVC tests related to market activities.
 */
@SpringMvcDbTest
internal class PriceControllerRefreshTests
    @Autowired
    private constructor(
        dateUtils: DateUtils,
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig
    ) {
        @MockitoBean
        private lateinit var jwtDecoder: JwtDecoder

        @MockitoBean
        private lateinit var alphaPriceService: AlphaPriceService

        @MockitoBean
        private lateinit var assetService: AssetService

        @MockitoBean
        private lateinit var assetFinder: AssetFinder

        @MockitoBean
        private lateinit var priceService: PriceService

        @Autowired
        private lateinit var marketDataService: MarketDataService

        @Autowired
        private lateinit var mdFactory: MdFactory
        private var testDate = dateUtils.getDate()

        private val asset =
            Asset(
                code = "DUMMY",
                market = US
            )
        private val priceRequest =
            PriceRequest(
                date = testDate.toString(),
                assets = listOf(PriceAsset(asset))
            )

        @BeforeEach
        fun mockAlphaPriceService() {
            `when`(assetService.find(asset.id)).thenReturn(asset)
            `when`(assetFinder.find(asset.id)).thenReturn(asset)
            `when`(alphaPriceService.isMarketSupported(US))
                .thenReturn(true)

            `when`(alphaPriceService.getId())
                .thenReturn(AlphaPriceService.ID)

            assertThat(mdFactory.getMarketDataProvider(US).getId())
                .isEqualTo(AlphaPriceService.ID)
            `when`(
                alphaPriceService.getDate(
                    asset.market,
                    priceRequest
                )
            ).thenReturn(testDate)

            // Mock the getMarketDataCount method - data already exists, so refresh should not fetch new data
            `when`(priceService.getMarketDataCount(any<String>(), any<java.time.LocalDate>()))
                .thenReturn(1L) // Data exists, so refresh should return early
        }

        @Test
        @WithMockUser(
            username = "test-user",
            roles = [AuthConstants.USER]
        )
        fun is_refreshServiceTestWorking() {
            // Start price setup
            `when`(alphaPriceService.getMarketData(priceRequest = priceRequest))
                .thenReturn(
                    listOf(
                        MarketData(
                            asset = asset,
                            close = BigDecimal("10.00")
                        )
                    )
                )

            val response = marketDataService.getPriceResponse(priceRequest)
            assertThat(response.data)
                .isNotNull
                .hasSize(1)

            assertThat(
                response.data
                    .iterator()
                    .next()
                    .close
            ).isEqualTo(BigDecimal("10.00"))
            // End setup

            // TEST perform refresh. Since data already exists, refresh should not fetch new data
            // The price should remain 10.00 (existing data), not change to 11.00
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/prices/refresh/{assetId}", asset.id)
                        .param("asAt", testDate.toString())
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(
                    MockMvcResultMatchers.status().isOk
                )

            // Verify that the existing price (10.00) is maintained, not overwritten
            assertThat(
                marketDataService
                    .getPriceResponse(priceRequest)
                    .data
                    .iterator()
                    .next()
                    .close
            ).isEqualTo(BigDecimal("10.00"))
        }
    }