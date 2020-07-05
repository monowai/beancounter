package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.toKey
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("assets")
@Tag("slow")
internal class AssetControllerTest {
    private val authorityRoleConverter = AuthorityRoleConverter()

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
        token = TokenUtils.getUserToken(user)
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
        var mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/assets")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(objectMapper.writeValueAsString(assetRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper.readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        assertThat(data).hasSize(2)


        // marketCode is for persistence only,  Clients should rely on the
        //   hydrated Market object
        assertThat(data[toKey(firstAsset)])
                .isNotNull()
                .hasFieldOrProperty("id")
                .hasFieldOrProperty("market")
                .hasFieldOrPropertyWithValue("name", firstAsset.name)
                .hasFieldOrPropertyWithValue("code", firstAsset.code.toUpperCase())
                .hasFieldOrPropertyWithValue("marketCode", null)
                .hasFieldOrProperty("id")
        assertThat(data[toKey(secondAsset)])
                .isNotNull()
                .hasFieldOrProperty("id")
                .hasFieldOrProperty("market")
                .hasFieldOrPropertyWithValue("code", secondAsset.code.toUpperCase())
                .hasFieldOrPropertyWithValue("marketCode", null)
                .hasFieldOrProperty("id")
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
                .isEqualToComparingFieldByField(asset)

        // By Market/Asset
        mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{marketCode}/{assetCode}",
                        asset.market.code,
                        asset.code)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(objectMapper.readValue(
                mvcResult.response.contentAsString, AssetResponse::class.java)
                .data)
                .isEqualToComparingFieldByField(asset)
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
                .isNotNull()
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
        assertThat(updatedAsset).isEqualToComparingFieldByField(createdAsset)
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
                .isNotNull()
                .isInstanceOfAny(BusinessException::class.java)

        // Invalid Asset
        result = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/MOCK/blah")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
                .isNotNull()
                .isInstanceOfAny(BusinessException::class.java)

        result = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{assetId}", "doesn't exist")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        assertThat(result.andReturn().resolvedException)
                .isNotNull()
                .isInstanceOfAny(BusinessException::class.java)
    }
}