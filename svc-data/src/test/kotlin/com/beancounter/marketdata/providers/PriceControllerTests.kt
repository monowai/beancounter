package com.beancounter.marketdata.providers

import com.beancounter.auth.AutoConfigureMockAuth
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
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * MVC tests related to market activities.
 */
@SpringBootTest
@Tag("db")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
internal class PriceControllerTests
    @Autowired
    private constructor(
        private val mockMvc: MockMvc,
        private val mockAuthConfig: MockAuthConfig,
        private val bcJson: BcJson,
        private val mdFactory: MdFactory,
    ) {
        @MockBean
        private lateinit var marketDataRepo: MarketDataRepo

        private lateinit var priceDate: LocalDate
        private val mockPrice = BigDecimal("999.99")
        private val asset: Asset = Asset(code = "dummy", market = US)

        @MockBean
        private lateinit var assetService: AssetService

        @BeforeEach
        fun setUp() {
            Mockito.`when`(
                assetService.find(asset.id),
            ).thenReturn(asset)
            Mockito.`when`(
                assetService.findLocally(AssetInput(asset.market.code, asset.code)),
            ).thenReturn(asset)

            val marketDataProvider = mdFactory.getMarketDataProvider(asset.market)
            priceDate =
                marketDataProvider.getDate(
                    asset.market,
                    PriceRequest.of(AssetInput(market = asset.market.code, code = asset.code)),
                )
            Mockito.`when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, priceDate))
                .thenReturn(
                    Optional.of(
                        MarketData(
                            asset,
                            close = mockPrice,
                            open = mockPrice,
                            priceDate = priceDate,
                        ),
                    ),
                )
        }

        @Test
        fun is_ContextLoaded() {
            assertThat(mockMvc).isNotNull
        }

        @Test
        @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
        fun is_MarketsReturned() {
            val json =
                mockMvc.perform(
                    MockMvcRequestBuilders.get("/markets")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE),
                )
                    .andExpect(
                        MockMvcResultMatchers.status().isOk,
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE),
                    ).andReturn()
                    .response.contentAsString
            val (data) = bcJson.objectMapper.readValue(json, MarketResponse::class.java)
            assertThat(data).isNotNull.isNotEmpty
        }

        @Test
        @Tag("slow")
        @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
        fun is_PriceFromMarketAssetFound() {
            val json =
                mockMvc.perform(
                    MockMvcRequestBuilders.get(
                        "/prices/{marketId}/{assetId}",
                        asset.market.code,
                        asset.code,
                    )
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE),
                )
                    .andExpect(
                        MockMvcResultMatchers.status().isOk,
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE),
                    ).andReturn().response.contentAsString
            val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
            assertThat(data).isNotNull.hasSize(1)
            val marketData = data.iterator().next()
            assertThat(marketData)
                .hasFieldOrPropertyWithValue("asset.id", asset.id)
                .hasFieldOrPropertyWithValue("open", mockPrice)
                .hasFieldOrPropertyWithValue("priceDate", priceDate)
        }

        private val assetCode = "assetCode"

        @Test
        @Tag("slow")
        @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
        fun is_MdCollectionReturnedForAssets() {
            Mockito.`when`(assetService.findLocally(AssetInput(CASH_MARKET.code, assetCode)))
                .thenReturn(getTestAsset(CASH_MARKET, assetCode))
            val assetInputs: MutableCollection<PriceAsset> = ArrayList()
            assetInputs.add(
                PriceAsset(getTestAsset(CASH_MARKET, assetCode)),
            )
            val json =
                mockMvc.perform(
                    MockMvcRequestBuilders.post("/prices")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                        .content(
                            bcJson.objectMapper.writeValueAsString(
                                PriceRequest(assets = assetInputs),
                            ),
                        ),
                )
                    .andExpect(
                        MockMvcResultMatchers.status().isOk,
                    ).andExpect(
                        MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE),
                    ).andReturn()
                    .response
                    .contentAsString
            val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
            assertThat(data)
                .isNotNull
                .hasSize(assetInputs.size)
        }

        @Test
        @Tag("slow")
        @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
        fun is_OffMarketPriceWritten() {
            val offMarketAsset = getTestAsset(market = Market(OffMarketDataProvider.ID), code = assetCode)
            val offMarketPrice =
                OffMarketPriceRequest(
                    offMarketAsset.id,
                    closePrice = BigDecimal("999.0"),
                )

            Mockito.`when`(assetService.find(assetCode))
                .thenReturn(offMarketAsset)

            Mockito.`when`(
                marketDataRepo.save(
                    MarketData(
                        asset = offMarketAsset,
                        priceDate = DateUtils().getDate(offMarketPrice.date),
                        close = offMarketPrice.closePrice,
                    ),
                ),
            ).thenReturn(
                MarketData(asset = offMarketAsset, close = offMarketPrice.closePrice),
            )
            val json =
                mockMvc.perform(
                    MockMvcRequestBuilders.post("/prices/write")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                        .content(
                            bcJson.objectMapper.writeValueAsString(
                                offMarketPrice,
                            ),
                        ),
                ).andExpect(
                    MockMvcResultMatchers.status().isOk,
                ).andExpect(
                    MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE),
                ).andReturn()
                    .response
                    .contentAsString
            val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
            assertThat(data)
                .isNotNull
                .hasSize(1)
            assertThat(data.iterator().next())
                .hasFieldOrPropertyWithValue("close", offMarketPrice.closePrice)
        }

        @Test
        @Tag("slow")
        @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
        fun is_ValuationRequestHydratingAssets() {
            val json =
                mockMvc.perform(
                    MockMvcRequestBuilders.get(
                        "/prices/{marketCode}/{assetCode}",
                        asset.market.code,
                        asset.code,
                    ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE),
                ).andExpect(
                    MockMvcResultMatchers.status().isOk,
                ).andExpect(
                    MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE),
                ).andReturn()
                    .response
                    .contentAsString
            val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
            assertThat(data).isNotNull.hasSize(1)
            assertThat(data.iterator().next())
                .hasFieldOrProperty("asset.id")
                .hasFieldOrPropertyWithValue("asset.market.code", asset.market.code)
                .hasFieldOrPropertyWithValue("open", mockPrice)
                .hasFieldOrPropertyWithValue("priceDate", priceDate)
        }
    }
