package com.beancounter.marketdata.currency

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.TokenUtils
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.Payload
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringMvcDbTest
internal class CurrencyMvcTests {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var authConfig: AuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun is_CurrencyDataReturning() {
        val token = TokenUtils(authConfig).getSystemUserToken(SystemUser("currencies"))
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/currencies")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val currencyResponse =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    CurrencyResponse::class.java
                )
        Assertions.assertThat(currencyResponse).isNotNull.hasFieldOrProperty(Payload.DATA)
        Assertions.assertThat(currencyResponse.data).isNotEmpty
    }
}