package com.beancounter.marketdata.integ

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants.Companion.MOCK
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.mock.MockProviderService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
/**
 * MVC tests related to market activities.
 */
internal class MarketDataControllerTests @Autowired private constructor(
    private val wac: WebApplicationContext,
    marketService: MarketService,
    private val mockProviderService: MockProviderService,
    private val bcJson: BcJson,
) {
    private val dummy: Asset = getAsset(
        marketService.getMarket("mock"), "dummy"
    )

    // Represents dummy after it has been Jackson'ized
    private val dummyJsonAsset: Asset = this.bcJson.objectMapper
        .readValue(this.bcJson.objectMapper.writeValueAsString(dummy), Asset::class.java)

    @MockBean
    private lateinit var assetService: AssetService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        Assertions.assertThat(dummy.id).isNotNull
        Mockito.`when`(
            assetService.find(dummy.id)
        ).thenReturn(dummy)
        Mockito.`when`(
            assetService.findLocally(dummy.market.code, dummy.code)
        )
            .thenReturn(dummy)
    }

    @Test
    fun is_ContextLoaded() {
        Assertions.assertThat(wac).isNotNull
    }

    @Test
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    @Throws(
        Exception::class
    )
    fun is_MarketsReturned() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/markets")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        )
            .andExpect(
                MockMvcResultMatchers.status().isOk
            ).andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
            ).andReturn()
            .response.contentAsString
        val (data) = bcJson.objectMapper.readValue(json, MarketResponse::class.java)
        Assertions.assertThat(data).isNotNull.isNotEmpty
    }

    @Test
    @Tag("slow")
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    @Throws(
        Exception::class
    )
    fun is_PriceFormMarketAssetFound() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/prices/{marketId}/{assetId}",
                dummy.market.code,
                dummy.code
            )
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        )
            .andExpect(
                MockMvcResultMatchers.status().isOk
            ).andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
            ).andReturn().response.contentAsString
        val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
        Assertions.assertThat(data).isNotNull.hasSize(1)
        val marketData = data.iterator().next()
        Assertions.assertThat(marketData)
            .hasFieldOrPropertyWithValue("asset", dummyJsonAsset)
            .hasFieldOrPropertyWithValue("open", BigDecimal.valueOf(999.99))
            .hasFieldOrPropertyWithValue("priceDate", mockProviderService.priceDate)
    }

    private val assetCode = "assetCode"

    @Test
    @Tag("slow")
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    @Throws(
        Exception::class
    )
    fun is_MdCollectionReturnedForAssets() {
        Mockito.`when`(assetService.findLocally(MOCK.code, assetCode))
            .thenReturn(getAsset(MOCK, assetCode))
        val assetInputs: MutableCollection<AssetInput> = ArrayList()
        assetInputs.add(
            getAssetInput(MOCK.code, assetCode)
        )
        val json = mockMvc.perform(
            MockMvcRequestBuilders.post("/prices")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
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
        Assertions.assertThat(data)
            .isNotNull
            .hasSize(assetInputs.size)
    }

    @Test
    @Tag("slow")
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    @Throws(
        Exception::class
    )
    fun is_ValuationRequestHydratingAssets() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/prices/{marketId}/{assetId}",
                dummy.market.code,
                dummy.code
            )
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(
            MockMvcResultMatchers.status().isOk
        ).andExpect(
            MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andReturn()
            .response
            .contentAsString
        val (data) = bcJson.objectMapper.readValue(json, PriceResponse::class.java)
        Assertions.assertThat(data).isNotNull.hasSize(1)
        val marketData = data.iterator().next()
        Assertions.assertThat(marketData)
            .hasFieldOrPropertyWithValue("asset", dummyJsonAsset)
            .hasFieldOrPropertyWithValue("open", BigDecimal.valueOf(999.99))
            .hasFieldOrPropertyWithValue("priceDate", mockProviderService.priceDate)
    }
}
