package com.beancounter.marketdata.registration

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Verify that an unauthenticated user can request a token with correct credentials.
 */
@SpringMvcDbTest
@AutoConfigureMockAuth
class AuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var authGateway: LoginService.AuthGateway

    @Autowired
    private lateinit var loginService: LoginService

    @Test
    fun unauthenticatedUserCanRequestTokenByPassword() {
        val loginRequest =
            LoginRequest(
                "user",
                "password"
            )
        val mockResponse =
            OpenIdResponse(
                "abc",
                "scope",
                0L,
                "type"
            )
        Mockito
            .`when`(
                authGateway
                    .login(
                        loginService
                            .passwordRequest(
                                loginRequest.user,
                                loginRequest.password
                            )
                    )
            ).thenReturn(mockResponse)
        val performed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/auth")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
                .andReturn()
        val response =
            objectMapper.readValue(
                performed.response.contentAsString,
                OpenIdResponse::class.java
            )
        assertThat(response).isNotNull().usingRecursiveComparison().isEqualTo(mockResponse)
    }
}