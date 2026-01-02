package com.beancounter.marketdata.assets

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.ASSET_ROOT
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Locale

private const val ID = "id"

private const val NAME = "name"

@SpringMvcDbTest
internal class AssetControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    fun setup(enrichmentFactory: EnrichmentFactory) {
        enrichmentFactory.register(DefaultEnricher())
    }

    @Test
    fun `create asset then find by id`() {
        val firstAsset =
            getTestAsset(
                market = NASDAQ,
                code = "MyCode"
            )
        val secondAsset =
            getTestAsset(
                market = NASDAQ,
                code = "Second"
            )
        val assetInputMap =
            mapOf(
                toKey(firstAsset) to
                    getAssetInput(
                        NASDAQ.code,
                        firstAsset.code
                    ),
                toKey(secondAsset) to
                    getAssetInput(
                        NASDAQ.code,
                        secondAsset.code
                    )
            )
        var mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post(ASSET_ROOT)
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).content(objectMapper.writeValueAsString(AssetRequest(assetInputMap)))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                ).andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        val (data) = objectMapper.readValue<AssetUpdateResponse>(mvcResult.response.contentAsString)
        assertThat(data).hasSize(assetInputMap.size)
        isAssetValid(
            data[toKey(firstAsset)]!!,
            firstAsset
        )
        isAssetValid(
            data[toKey(secondAsset)]!!,
            secondAsset
        )

        val asset = data[toKey(secondAsset)]
        mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "$ASSET_ROOT/{assetId}",
                            asset!!.id
                        ).with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        )
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        val (data1) =
            objectMapper.readValue(
                mvcResult.response.contentAsString,
                AssetResponse::class.java
            )
        assertThat(data1).isEqualTo(asset)

        mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "$ASSET_ROOT/{marketCode}/{assetCode}",
                            asset.market.code,
                            asset.code
                        ).with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        )
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        assertThat(
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    AssetResponse::class.java
                ).data
        ).isEqualTo(asset)
    }

    private fun isAssetValid(
        dataAsset: Asset,
        asset: Asset
    ) {
        assertThat(dataAsset)
            .isNotNull
            .hasFieldOrProperty(ID)
            .hasFieldOrProperty("market")
            .hasFieldOrPropertyWithValue(
                NAME,
                asset.name
            ).hasFieldOrPropertyWithValue(
                "code",
                asset.code.uppercase(Locale.getDefault())
            ).hasFieldOrPropertyWithValue(
                "marketCode",
                NASDAQ.code
            ).hasFieldOrPropertyWithValue(
                "version",
                "1"
            ).hasFieldOrPropertyWithValue(
                "status",
                Status.Active
            )
    }

    @Test
    fun `create same asset twice has same ID`() {
        var asset =
            AssetInput(
                NASDAQ.code,
                "MyCodeX",
                "\"quotes should be removed\"",
                null
            )
        var assetRequest = AssetRequest(mapOf(toKey(asset) to asset))
        var mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post(ASSET_ROOT)
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).content(objectMapper.writeValueAsBytes(assetRequest))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        var assetUpdateResponse =
            objectMapper.readValue<AssetUpdateResponse>(mvcResult.response.contentAsString)
        val createdAsset = assetUpdateResponse.data[toKey(asset)]
        assertThat(createdAsset)
            .isNotNull
            .hasFieldOrProperty(ID)
            .hasFieldOrPropertyWithValue(
                NAME,
                "quotes should be removed"
            ).hasFieldOrProperty("market")

        asset =
            AssetInput(
                NASDAQ.code,
                "MyCodeX",
                "Random Change",
                null
            )
        assetRequest = AssetRequest(mapOf(toKey(asset) to asset))
        mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post(ASSET_ROOT)
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).with(csrf())
                        .content(objectMapper.writeValueAsBytes(assetRequest))
                        .contentType(APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        assetUpdateResponse =
            objectMapper.readValue<AssetUpdateResponse>(mvcResult.response.contentAsString)
        val updatedAsset = assetUpdateResponse.data[toKey(asset)]
        assertThat(updatedAsset).isEqualTo(createdAsset)
    }

    @Test
    fun `missing asset throws appropriate error`() {
        // Invalid market code - throws BusinessException (BAD_REQUEST)
        var result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("$ASSET_ROOT/twee/blah")
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).with(csrf())
                        .contentType(APPLICATION_JSON)
                ).andExpect(status().isBadRequest)
        assertThat(result.andReturn().resolvedException!!)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)

        // Valid market but missing asset - throws BusinessException (BAD_REQUEST)
        result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("$ASSET_ROOT/MOCK/blah")
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).contentType(APPLICATION_JSON)
                ).andExpect(status().isBadRequest)
        assertThat(result.andReturn().resolvedException!!)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)

        // Asset ID not found - throws NotFoundException (NOT_FOUND)
        result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "$ASSET_ROOT/{assetId}",
                            "doesn't exist"
                        ).with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(mockAuthConfig.getUserToken())
                        ).contentType(APPLICATION_JSON)
                ).andExpect(status().isNotFound)
        assertThat(result.andReturn().resolvedException!!)
            .isNotNull
            .isInstanceOfAny(NotFoundException::class.java)
    }
}