package com.beancounter.marketdata.portfolio

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.PORTFOLIO_BY_CODE
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.PORTFOLIO_BY_ID
import com.beancounter.marketdata.utils.RegistrationUtils
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.Locale
import java.util.UUID

/**
 * Edge case scenario testing.
 *
 * Verifies correct exceptions thrown by the portfolio controller.
 *
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
@EnableWebSecurity
class PortfolioExceptionTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt

    @Autowired
    fun registerUser() {
        token =
            RegistrationUtils.registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser()),
            )
    }

    @Test
    fun notFoundByCode() {
        // Assert not found
        mockMvc.perform(
            MockMvcRequestBuilders.get(PORTFOLIO_BY_CODE, "does not exist")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun notFoundById() {
        mockMvc.perform(
            MockMvcRequestBuilders.get(PORTFOLIO_BY_ID, "invalidId")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
    }

    @Test
    fun uniqueConstraintInPlace() {
        val portfolioInput =
            PortfolioInput(
                UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                "is_UniqueConstraintInPlace",
                Constants.USD.code,
                Constants.NZD.code,
            )
        // Code and Owner are the same so, reject
        // Can't create two portfolios with the same code
        val result =
            mockMvc.perform(
                MockMvcRequestBuilders.post(BcMvcHelper.PORTFOLIO_ROOT)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .with(
                        SecurityMockMvcRequestPostProcessors.csrf(),
                    ).content(
                        objectMapper
                            .writeValueAsBytes(PortfoliosRequest(arrayListOf(portfolioInput, portfolioInput))),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isConflict)
                .andReturn()
        Assertions.assertThat(result.resolvedException)
            .isNotNull
            .isInstanceOfAny(DataIntegrityViolationException::class.java)
    }

    @Test
    @WithMockUser(username = "unregisteredUser", authorities = [AuthConstants.SCOPE_BC])
    fun unregisteredUserRejected() {
        val portfolioInput =
            PortfolioInput(
                UUID.randomUUID().toString().uppercase(Locale.getDefault()),
                "is_UnregisteredUserRejected",
                Constants.USD.code,
                Constants.NZD.code,
            )
        // Authenticated, but unregistered user can't create portfolios
        mockMvc.perform(
            MockMvcRequestBuilders.post(BcMvcHelper.PORTFOLIO_ROOT)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(
                    objectMapper
                        .writeValueAsBytes(PortfoliosRequest(arrayListOf(portfolioInput))),
                )
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
    }
}
