package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureWireMock(port = 0)
/**
 * MVC tests for Assets
 */
internal class AssetControllerTest {
    private val authorityRoleConverter = AuthorityRoleConverter()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var context: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    @Autowired
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()

        // Setup a user account
        val user = SystemUser("user", "user@testing.com")
        token = TokenUtils().getUserToken(user)
        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    @Throws(Exception::class)
    fun is_AssetCreationAndFindByWorking() {
        val firstAsset = getAsset("MOCK", "MyCode")
        val secondAsset = getAsset("MOCK", "Second")
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap[toKey(firstAsset)] = getAssetInput("MOCK", "MyCode")
        assetInputMap[toKey(secondAsset)] = getAssetInput("MOCK", "Second")
        val assetRequest = AssetRequest(assetInputMap)
        var mvcResult = postAssets(assetRequest)
        val (data) = objectMapper.readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        assertThat(data).hasSize(2)

        // marketCode is for persistence only,  Clients should rely on the
        //   hydrated Market object
        isAssetValid(data[toKey(firstAsset)], firstAsset)
        isAssetValid(data[toKey(secondAsset)], secondAsset)
        val asset = data[toKey(secondAsset)]
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/assets/{assetId}", asset!!.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        // Find by Primary Key
        val (data1) = objectMapper.readValue(
            mvcResult.response.contentAsString, AssetResponse::class.java
        )
        assertThat(data1)
            .usingRecursiveComparison().isEqualTo(asset)

        // By Market/Asset
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/assets/{marketCode}/{assetCode}",
                asset.market.code,
                asset.code
            )
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

        assertThat(
            objectMapper.readValue(
                mvcResult.response.contentAsString, AssetResponse::class.java
            ).data
        )
            .usingRecursiveComparison().isEqualTo(asset)
    }

    private fun postAssets(assetRequest: AssetRequest) = mockMvc.perform(
        MockMvcRequestBuilders.post("/assets")
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsString(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    )
        .andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andReturn()

    private fun isAssetValid(
        dataAsset: Asset?,
        firstAsset: Asset
    ) {
        assertThat(dataAsset)
            .isNotNull
            .hasFieldOrProperty("id")
            .hasFieldOrProperty("market")
            .hasFieldOrPropertyWithValue("name", firstAsset.name)
            .hasFieldOrPropertyWithValue("code", firstAsset.code.toUpperCase())
            .hasFieldOrPropertyWithValue("marketCode", null)
            .hasFieldOrProperty("id")
    }

    @Test
    @Throws(Exception::class)
    fun is_PostSameAssetTwiceBehaving() {
        var asset = AssetInput("MOCK", "MyCodeX", "\"quotes should be removed\"", null)
        var assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap[toKey(asset)] = asset
        var assetRequest = AssetRequest(assetInputMap)
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/assets")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(assetRequest))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        var assetUpdateResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        val createdAsset = assetUpdateResponse.data[toKey(asset)]
        assertThat(createdAsset)
            .isNotNull
            .hasFieldOrProperty("id")
            .hasFieldOrPropertyWithValue("name", "quotes should be removed")
            .hasFieldOrProperty("market")

        // Send it a second time, should not change
        asset = AssetInput("MOCK", "MyCodeX", "Random Change", null)
        assetInputMap = HashMap()
        assetInputMap[toKey(asset)] = asset
        assetRequest = AssetRequest(assetInputMap)
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/assets/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(assetRequest))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assetUpdateResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        val updatedAsset = assetUpdateResponse.data[toKey(asset)]
        assertThat(updatedAsset).usingRecursiveComparison().isEqualTo(createdAsset)
    }

    @Test
    @Throws(Exception::class)
    fun is_MissingAssetBadRequest() {
        // Invalid market
        var result = mockMvc.perform(
            MockMvcRequestBuilders.get("/assets/twee/blah")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)

        // Invalid Asset
        result = mockMvc.perform(
            MockMvcRequestBuilders.get("/assets/MOCK/blah")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)

        result = mockMvc.perform(
            MockMvcRequestBuilders.get("/assets/{assetId}", "doesn't exist")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)
    }
}
