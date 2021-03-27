package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.MarketDataBoot
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
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
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("test")
@Tag("slow")
internal class CurrencyMvcTests {
    private val objectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var context: WebApplicationContext
    private var mockMvc: MockMvc? = null

    @Autowired
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    @Test
    @Throws(Exception::class)
    fun is_CurrencyDataReturning() {
        val token = TokenUtils().getUserToken(SystemUser("currencies"))
        val mvcResult = mockMvc!!.perform(
            MockMvcRequestBuilders.get("/currencies/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(AuthorityRoleConverter()))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val currencyResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, CurrencyResponse::class.java)
        Assertions.assertThat(currencyResponse).isNotNull.hasFieldOrProperty("data")
        Assertions.assertThat(currencyResponse.data).isNotEmpty
    }
}
