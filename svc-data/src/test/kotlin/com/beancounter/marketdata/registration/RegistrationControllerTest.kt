package com.beancounter.marketdata.registration

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.RegistrationRequest
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Verify user registration behaviour.
 */
@SpringMvcDbTest
class RegistrationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun is_RegisterMeWorking() {
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        registerUser(mockMvc, token)
        mockMvc.perform(
            MockMvcRequestBuilders.post("/register")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(objectMapper.writeValueAsString(RegistrationRequest()))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andReturn()

        val meResult =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/me")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
                .andReturn()

        assertThat(meResult.response.status).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun is_MeWithNoToken() {
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        registerUser(mockMvc, token)
        val performed =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/me")
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun is_MeUnregistered() {
        val user =
            SystemUser(
                "is_MeUnregistered",
                "is_MeUnregistered@testing.com",
            )
        val token = mockAuthConfig.getUserToken(user)
        val performed =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/me")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isForbidden)
                .andReturn()
        assertThat(performed.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }
}
