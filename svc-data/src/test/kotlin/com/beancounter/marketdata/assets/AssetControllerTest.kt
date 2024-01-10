package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.ASSET_ROOT
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Locale

/**
 * MVC tests for Assets
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
internal class AssetControllerTest(
    @Autowired var enrichmentFactory: EnrichmentFactory,
) {
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig
    private val pId = "id"
    private val pName = "name"

    @Autowired
    fun setup(enrichmentFactory: EnrichmentFactory) {
        enrichmentFactory.register(DefaultEnricher())
    }

    @Test
    fun is_AssetCreationAndFindByWorking() {
        val firstAsset = getTestAsset(market = NASDAQ, code = "MyCode")
        val secondAsset = getTestAsset(market = NASDAQ, code = "Second")
        val assetInputMap =
            mapOf(
                Pair(toKey(firstAsset), getAssetInput(NASDAQ.code, firstAsset.code)),
                Pair(toKey(secondAsset), getAssetInput(NASDAQ.code, secondAsset.code)),
            )
        var mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.post(ASSET_ROOT)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .content(objectMapper.writeValueAsString(AssetRequest(assetInputMap)))
                    .with(csrf())
                    .contentType(APPLICATION_JSON),
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper.readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        assertThat(data).hasSize(2)

        // marketCode is for persistence only,  Clients should rely on the
        //   hydrated Market object
        isAssetValid(data[toKey(firstAsset)]!!, firstAsset)
        isAssetValid(data[toKey(secondAsset)]!!, secondAsset)
        val asset = data[toKey(secondAsset)]
        mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.get("$ASSET_ROOT/{assetId}", asset!!.id)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken())),
            ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        // Find by Primary Key
        val (data1) =
            objectMapper.readValue(
                mvcResult.response.contentAsString,
                AssetResponse::class.java,
            )
        assertThat(data1).isEqualTo(asset)

        // By Market/Asset
        mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.get(
                    "$ASSET_ROOT/{marketCode}/{assetCode}",
                    asset.market.code,
                    asset.code,
                )
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken())),
            ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        assertThat(
            objectMapper.readValue(
                mvcResult.response.contentAsString,
                AssetResponse::class.java,
            ).data,
        ).isEqualTo(asset)
    }

    private fun isAssetValid(
        dataAsset: Asset,
        asset: Asset,
    ) {
        assertThat(dataAsset)
            .isNotNull
            .hasFieldOrProperty(pId)
            .hasFieldOrProperty("market")
            .hasFieldOrPropertyWithValue(pName, asset.name)
            .hasFieldOrPropertyWithValue("code", asset.code.uppercase(Locale.getDefault()))
            .hasFieldOrPropertyWithValue("marketCode", NASDAQ.code)
            .hasFieldOrPropertyWithValue("version", "1")
            .hasFieldOrPropertyWithValue("status", Status.Active)
            .hasFieldOrProperty(pId)
    }

    @Test
    fun is_PostSameAssetTwiceBehaving() {
        var asset = AssetInput(NASDAQ.code, "MyCodeX", "\"quotes should be removed\"", null)
        var assetRequest = AssetRequest(mapOf(Pair(toKey(asset), asset)))
        var mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.post(ASSET_ROOT)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .content(objectMapper.writeValueAsBytes(assetRequest))
                    .with(csrf())
                    .contentType(APPLICATION_JSON),
            ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()
        var assetUpdateResponse =
            objectMapper
                .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        val createdAsset = assetUpdateResponse.data[toKey(asset)]
        assertThat(createdAsset)
            .isNotNull
            .hasFieldOrProperty(pId)
            .hasFieldOrPropertyWithValue(pName, "quotes should be removed")
            .hasFieldOrProperty("market")

        // Send it a second time, should not change
        asset = AssetInput(NASDAQ.code, "MyCodeX", "Random Change", null)
        assetRequest = AssetRequest(mapOf(Pair(toKey(asset), asset)))
        mvcResult =
            mockMvc.perform(
                MockMvcRequestBuilders.post(ASSET_ROOT)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(assetRequest))
                    .contentType(APPLICATION_JSON),
            ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()
        assetUpdateResponse =
            objectMapper
                .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        val updatedAsset = assetUpdateResponse.data[toKey(asset)]
        assertThat(updatedAsset).isEqualTo(createdAsset)
    }

    @Test
    fun is_MissingAssetBadRequest() {
        // Invalid market
        var result =
            mockMvc.perform(
                MockMvcRequestBuilders.get("$ASSET_ROOT/twee/blah")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .with(csrf())
                    .contentType(APPLICATION_JSON),
            ).andExpect(status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)

        // Invalid Asset
        result =
            mockMvc.perform(
                MockMvcRequestBuilders.get("$ASSET_ROOT/MOCK/blah")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .contentType(APPLICATION_JSON),
            ).andExpect(status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)

        result =
            mockMvc.perform(
                MockMvcRequestBuilders.get("$ASSET_ROOT/{assetId}", "doesn't exist")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .contentType(APPLICATION_JSON),
            ).andExpect(status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)
    }
}
