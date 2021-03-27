package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
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

@ExtendWith(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
internal class MarketMvcTests {
    private val objectMapper = BcJson().objectMapper
    private val authorityRoleConverter = AuthorityRoleConverter()

    @Autowired
    private lateinit var wac: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private var token: Jwt = TokenUtils()
        .getUserToken(SystemUser("MarketMvcTests", "MarketMvcTests@testing.com"))

    @BeforeEach
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
        registerUser(mockMvc, token)
    }

    @Test
    @Throws(Exception::class)
    fun is_AllMarketsFound() {
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/markets/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val marketResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, MarketResponse::class.java)
        Assertions.assertThat(marketResponse).isNotNull.hasFieldOrProperty("data")
        Assertions.assertThat(marketResponse.data).isNotEmpty
    }

    @Test
    @Throws(Exception::class)
    fun is_SingleMarketFoundCaseInsensitive() {
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/markets/nzx")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val marketResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, MarketResponse::class.java)
        Assertions.assertThat(marketResponse).isNotNull.hasFieldOrProperty("data")
        Assertions.assertThat(marketResponse.data).isNotNull.hasSize(1)
        val nzx = marketResponse.data!!.iterator().next()
        Assertions.assertThat(nzx).hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId", "enricher")
    }

    @Test
    @Throws(Exception::class)
    fun is_SingleMarketBadRequest() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/markets/non-existent")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        Assertions.assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)
    }
}
