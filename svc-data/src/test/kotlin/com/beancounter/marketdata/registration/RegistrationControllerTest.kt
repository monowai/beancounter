package com.beancounter.marketdata.registration

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Verify user registration behaviour.
 */
@SpringMvcDbTest
class RegistrationControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun `should register user successfully`() {
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        registerUser(
            mockMvc,
            token
        )
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/register")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .content(objectMapper.writeValueAsString(RegistrationRequest()))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andReturn()

        val meResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
                .andReturn()

        assertThat(meResult.response.status).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun `should return unauthorized when accessing me endpoint without token`() {
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        registerUser(
            mockMvc,
            token
        )
        val performed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `should return forbidden when accessing me endpoint as unregistered user`() {
        val user =
            SystemUser(
                "is_MeUnregistered",
                "is_MeUnregistered@testing.com"
            )
        val token = mockAuthConfig.getUserToken(user)
        val performed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isForbidden)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun `should return SystemUser when M2M token looks up by sub`() {
        // Register the target user first using their own user token.
        val target =
            SystemUser(
                email = "m2m-sub-lookup@testing.com",
                auth0 = "auth0|m2m-sub-user"
            )
        val userToken = mockAuthConfig.tokenUtils.getAuth0Token(target)
        registerUser(mockMvc, userToken)

        // M2M caller looks up the target via ?sub=
        val m2mToken = mockAuthConfig.tokenUtils.getSystemToken(Constants.systemUser)
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .param("sub", "auth0|m2m-sub-user")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(m2mToken))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val response =
            objectMapper.readValue(
                result.response.contentAsByteArray,
                RegistrationResponse::class.java
            )
        assertThat(response.data.email).isEqualTo("m2m-sub-lookup@testing.com")
        assertThat(response.data.auth0).isEqualTo("auth0|m2m-sub-user")
        assertThat(response.data.id).isNotBlank()
    }

    @Test
    fun `should return SystemUser when M2M token looks up by email`() {
        val target =
            SystemUser(
                email = "m2m-email-lookup@testing.com",
                auth0 = "auth0|m2m-email-user"
            )
        val userToken = mockAuthConfig.tokenUtils.getAuth0Token(target)
        registerUser(mockMvc, userToken)

        val m2mToken = mockAuthConfig.tokenUtils.getSystemToken(Constants.systemUser)
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .param("email", "m2m-email-lookup@testing.com")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(m2mToken))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

        val response =
            objectMapper.readValue(
                result.response.contentAsByteArray,
                RegistrationResponse::class.java
            )
        assertThat(response.data.email).isEqualTo("m2m-email-lookup@testing.com")
        assertThat(response.data.id).isNotBlank()
    }

    @Test
    fun `should return 404 when M2M looks up a sub with no SystemUser`() {
        val m2mToken = mockAuthConfig.tokenUtils.getSystemToken(Constants.systemUser)
        val performed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .param("sub", "auth0|never-registered")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(m2mToken))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isNotFound)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.NOT_FOUND.value())
    }

    @Test
    fun `should return bad request when M2M token calls me without sub or email`() {
        val m2mToken = mockAuthConfig.tokenUtils.getSystemToken(Constants.systemUser)
        val performed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(m2mToken))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `should return bad request when user token passes sub query param`() {
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        registerUser(mockMvc, token)
        val performed =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/me")
                        .param("sub", "auth0|someone-else")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }
}