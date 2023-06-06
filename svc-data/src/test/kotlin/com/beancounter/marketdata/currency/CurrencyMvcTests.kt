package com.beancounter.marketdata.currency

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.TokenUtils
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.Payload
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
internal class CurrencyMvcTests {
    private val objectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var authConfig: AuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun is_CurrencyDataReturning() {
        val token = TokenUtils(authConfig).getUserToken(SystemUser("currencies"))
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get("/currencies")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val currencyResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, CurrencyResponse::class.java)
        Assertions.assertThat(currencyResponse).isNotNull.hasFieldOrProperty(Payload.DATA)
        Assertions.assertThat(currencyResponse.data).isNotEmpty
    }
}
