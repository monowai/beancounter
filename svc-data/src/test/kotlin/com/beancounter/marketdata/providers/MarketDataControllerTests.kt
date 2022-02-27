package com.beancounter.marketdata.providers

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants.Companion.CASH
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
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
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
internal class MarketDataControllerTests @Autowired private constructor(
    private val mockMvc: MockMvc,
    private val mockAuthConfig: MockAuthConfig,
    private val bcJson: BcJson,
    private val mdFactory: MdFactory,
) {
    @MockBean
    private lateinit var marketDataRepo: MarketDataRepo

    private lateinit var priceDate: LocalDate
    private val mockPrice = BigDecimal("999.99")
    private val asset: Asset = Asset(market = NASDAQ, code = "dummy")

    @MockBean
    private lateinit var assetService: AssetService

    @BeforeEach
    fun setUp() {
        val marketDataProvider = mdFactory.getMarketDataProvider(asset.market)!!
        priceDate = marketDataProvider.getDate(asset.market, PriceRequest.of(AssetInput(asset)))
        Mockito.`when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, priceDate))
            .thenReturn(
                Optional.of(
                    MarketData(
                        asset,
                        close = mockPrice,
                        open = mockPrice,
                        priceDate = priceDate
                    )
                )
            )
        assertThat(asset.id).isNotNull
        Mockito.`when`(
            assetService.find(asset.id)
        ).thenReturn(asset)
        Mockito.`when`(
            assetService.findLocally(asset.market.code, asset.code)
        )
            .thenReturn(asset)
    }

    @Test
    fun is_ContextLoaded() {
        assertThat(mockMvc).isNotNull
    }

    @Test
    @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
    @Throws(
        Exception::class
    )
    fun is_MarketsReturned() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/markets")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        )
            .andExpect(
                MockMvcResultMatchers.status().isOk
            ).andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
            ).andReturn()
            .response.contentAsString
        val (data) = bcJson.objectMapper.readValue(json, MarketResponse::class.java)
        assertThat(data).isNotNull.isNotEmpty
    }

    @Test
    @Tag("slow")
    @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
    @Throws(
        Exception::class
    )
    fun is_PriceFormMarketAssetFound() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/prices/{marketId}/{assetId}",
                asset.market.code,
                asset.code
            )
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        )
            .andExpect(
                MockMvcResultMatchers.status().isOk
            ).andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
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
    @Throws(
        Exception::class
    )
    fun is_MdCollectionReturnedForAssets() {
        Mockito.`when`(assetService.findLocally(CASH.code, assetCode))
            .thenReturn(getAsset(CASH, assetCode))
        val assetInputs: MutableCollection<PriceAsset> = ArrayList()
        assetInputs.add(
            PriceAsset(CASH.code, assetCode)
        )
        val json = mockMvc.perform(
            MockMvcRequestBuilders.post("/prices")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                .content(
                    bcJson.objectMapper.writeValueAsString(
                        PriceRequest(assets = assetInputs)
                    )
                )
        )
            .andExpect(
                MockMvcResultMatchers.status().isOk
            ).andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
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
    @Throws(
        Exception::class
    )
    fun is_ValuationRequestHydratingAssets() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/prices/{marketId}/{assetId}",
                asset.market.code,
                asset.code
            )
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(
            MockMvcResultMatchers.status().isOk
        ).andExpect(
            MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andReturn()
            .response
            .contentAsString
        val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
        assertThat(data).isNotNull.hasSize(1)
        assertThat(data.iterator().next())
            .hasFieldOrPropertyWithValue("asset.id", asset.id)
            .hasFieldOrPropertyWithValue("asset.market.code", asset.market.code)
            .hasFieldOrPropertyWithValue("open", mockPrice)
            .hasFieldOrPropertyWithValue("priceDate", priceDate)
    }
}
