package com.beancounter.marketdata.providers

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.OffMarketPriceRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * MVC tests related to market activities.
 */
@SpringMvcDbTest
internal class PriceControllerTests
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig,
        private val mdFactory: MdFactory
    ) {
        @MockBean
        private lateinit var marketDataRepo: MarketDataRepo

        private lateinit var priceDate: LocalDate
        private val mockPrice = BigDecimal("999.99")
        private val asset: Asset =
            Asset(
                code = "dummy",
                market = US
            )

        @MockBean
        private lateinit var assetService: AssetService

        @BeforeEach
        fun setUp() {
            `when`(
                assetService.find(asset.id)
            ).thenReturn(asset)
            `when`(
                assetService.findLocally(
                    AssetInput(
                        asset.market.code,
                        asset.code
                    )
                )
            ).thenReturn(asset)

            val marketDataProvider = mdFactory.getMarketDataProvider(asset.market)
            priceDate =
                marketDataProvider.getDate(
                    asset.market,
                    PriceRequest.of(
                        AssetInput(
                            market = asset.market.code,
                            code = asset.code
                        )
                    )
                )
            `when`(
                marketDataRepo.findByAssetIdAndPriceDate(
                    asset.id,
                    priceDate
                )
            ).thenReturn(
                Optional.of(
                    MarketData(
                        asset,
                        close = mockPrice,
                        open = mockPrice,
                        priceDate = priceDate
                    )
                )
            )
            `when`(
                marketDataRepo.findByAssetInAndPriceDate(
                    listOf(asset),
                    priceDate
                )
            ).thenReturn(
                listOf(
                    MarketData(
                        asset,
                        close = mockPrice,
                        open = mockPrice,
                        priceDate = priceDate
                    )
                )
            )
        }

        @Test
        fun is_ContextLoaded() {
            assertThat(mockMvc).isNotNull
        }

        @Test
        @WithMockUser(
            username = "test-user",
            roles = [AuthConstants.USER]
        )
        fun is_MarketsReturned() {
            val json =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/markets")
                            .with(
                                SecurityMockMvcRequestPostProcessors
                                    .jwt()
                                    .jwt(mockAuthConfig.getUserToken())
                            ).contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andExpect(
                        MockMvcResultMatchers.status().isOk
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andReturn()
                    .response.contentAsString
            val (data) =
                objectMapper.readValue(
                    json,
                    MarketResponse::class.java
                )
            assertThat(data).isNotNull.isNotEmpty
        }

        @Test
        @Tag("wiremock")
        @WithMockUser(
            username = "test-user",
            roles = [AuthConstants.USER]
        )
        fun is_PriceFromMarketAssetFound() {
            val json =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get(
                                "/prices/{marketId}/{assetId}",
                                asset.market.code,
                                asset.code
                            ).with(
                                SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken())
                            ).contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andExpect(
                        MockMvcResultMatchers.status().isOk
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andReturn()
                    .response.contentAsString
            val (data) = objectMapper.readValue<PriceResponse>(json)
            assertThat(data).isNotNull.hasSize(1)
            val marketData = data.iterator().next()
            assertThat(marketData)
                .hasFieldOrPropertyWithValue(
                    "asset.id",
                    asset.id
                ).hasFieldOrPropertyWithValue(
                    "open",
                    mockPrice
                ).hasFieldOrPropertyWithValue(
                    "priceDate",
                    priceDate
                )
        }

        private val assetCode = "assetCode"

        @Test
        @Tag("wiremock")
        @WithMockUser(
            username = "test-user",
            roles = [AuthConstants.USER]
        )
        fun is_MdCollectionReturnedForCashAssets() {
            val cashAsset =
                getTestAsset(
                    CASH_MARKET,
                    assetCode
                )
            val assetInput =
                listOf(
                    PriceAsset(
                        market = CASH_MARKET.code,
                        code = assetCode
                    )
                )

            val priceRequest =
                PriceRequest(
                    assets = assetInput
                )

            val resolved =
                PriceRequest(
                    assets =
                        listOf(
                            PriceAsset(
                                market = CASH_MARKET.code,
                                code = assetCode,
                                resolvedAsset = cashAsset
                            )
                        )
                )

            // Add in the resolved asset
            `when`(assetService.resolveAssets(priceRequest))
                .thenReturn(resolved)

            val json =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/prices")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .with(
                                SecurityMockMvcRequestPostProcessors
                                    .jwt()
                                    .jwt(mockAuthConfig.getUserToken())
                            ).content(
                                objectMapper.writeValueAsString(
                                    priceRequest
                                )
                            )
                    ).andExpect(
                        MockMvcResultMatchers.status().isOk
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andReturn()
                    .response.contentAsString
            val (data) =
                objectMapper.readValue(
                    json,
                    PriceResponse::class.java
                )
            assertThat(data).isNotNull.hasSize(assetInput.size)
        }

        @Test
        @Tag("wiremock")
        @WithMockUser(
            username = "test-user",
            roles = [AuthConstants.USER]
        )
        fun is_OffMarketPriceWritten() {
            val offMarketAsset =
                getTestAsset(
                    market = Market(OffMarketDataProvider.ID),
                    code = assetCode
                )
            val offMarketPrice =
                OffMarketPriceRequest(
                    offMarketAsset.id,
                    closePrice = BigDecimal("999.0")
                )

            `when`(assetService.find(assetCode)).thenReturn(offMarketAsset)

            `when`(
                marketDataRepo.save(
                    MarketData(
                        asset = offMarketAsset,
                        priceDate = DateUtils().getFormattedDate(offMarketPrice.date),
                        close = offMarketPrice.closePrice
                    )
                )
            ).thenReturn(
                MarketData(
                    asset = offMarketAsset,
                    close = offMarketPrice.closePrice
                )
            )
            val json =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post("/prices/write")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .with(
                                SecurityMockMvcRequestPostProcessors
                                    .jwt()
                                    .jwt(mockAuthConfig.getUserToken())
                            ).content(
                                objectMapper.writeValueAsString(
                                    offMarketPrice
                                )
                            )
                    ).andExpect(
                        MockMvcResultMatchers.status().isOk
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andReturn()
                    .response.contentAsString
            val (data) =
                objectMapper.readValue(
                    json,
                    PriceResponse::class.java
                )
            assertThat(data).isNotNull.hasSize(1)
            assertThat(data.iterator().next()).hasFieldOrPropertyWithValue(
                "close",
                offMarketPrice.closePrice
            )
        }

        @Test
        @Tag("wiremock")
        @WithMockUser(
            username = "test-user",
            roles = [AuthConstants.USER]
        )
        fun is_ValuationRequestHydratingAssets() {
            val json =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get(
                                "/prices/{marketCode}/{assetCode}",
                                asset.market.code,
                                asset.code
                            ).with(
                                SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken())
                            ).contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andExpect(
                        MockMvcResultMatchers.status().isOk
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                    ).andReturn()
                    .response.contentAsString
            val (priceResponse) = objectMapper.readValue<PriceResponse>(json)
            assertThat(priceResponse).isNotNull.hasSize(1)
            assertThat(priceResponse.iterator().next())
                .hasFieldOrProperty("asset.id")
                .hasFieldOrPropertyWithValue(
                    "asset.market.code",
                    asset.market.code
                ).hasFieldOrPropertyWithValue(
                    "priceDate",
                    priceDate
                ).hasFieldOrPropertyWithValue(
                    "open",
                    mockPrice
                )
        }
    }