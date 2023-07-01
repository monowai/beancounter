package com.beancounter.marketdata.registration

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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

/**
 * Verify that an unauthenticated user can request a token with correct credentials.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
class AuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var loginService: LoginService

    @Test
    fun unauthenticatedUserCanRequestTokenByPassword() {
        val loginRequest = LoginRequest("user", "password")
        val mockResponse = OpenIdResponse("abc", "scope", 0L, "type")
        Mockito.`when`(
            mockAuthConfig.authGateway
                .login(
                    loginService
                        .passwordRequest(loginRequest.user, loginRequest.password),
                ),
        )
            .thenReturn(mockResponse)
        val performed = mockMvc.perform(
            MockMvcRequestBuilders.post("/auth")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(BcJson().objectMapper.writeValueAsString(loginRequest))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andReturn()
        val response = BcJson().objectMapper.readValue(performed.response.contentAsString, OpenIdResponse::class.java)
        assertThat(response).isNotNull().usingRecursiveComparison().isEqualTo(mockResponse)
    }
}
