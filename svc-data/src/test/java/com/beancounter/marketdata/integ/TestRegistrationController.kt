package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
class TestRegistrationController {
    private lateinit var mockMvc: MockMvc
    private val tokenUtils = TokenUtils()

    @Autowired
    private lateinit var context: WebApplicationContext

    @BeforeEach
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    @Test
    @Throws(Exception::class)
    fun is_RegisterMeWorking() {
        val user = SystemUser("user", "user@testing.com")
        val token = tokenUtils.getUserToken(user)
        registerUser(mockMvc, token)
        val performed = mockMvc.perform(
            MockMvcRequestBuilders.get("/me")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(AuthorityRoleConverter()))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is2xxSuccessful)
            .andReturn()
        Assertions.assertThat(performed.response.status).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    @Throws(Exception::class)
    fun is_MeWithNoToken() {
        val user = SystemUser("user", "user@testing.com")
        val token = tokenUtils.getUserToken(user)
        registerUser(mockMvc, token)
        val performed = mockMvc.perform(
            MockMvcRequestBuilders.get("/me")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
        Assertions.assertThat(performed.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    @Throws(Exception::class)
    fun is_MeUnregistered() {
        val user = SystemUser(
            "is_MeUnregistered",
            "is_MeUnregistered@testing.com"
        )
        val token = tokenUtils.getUserToken(user)
        val performed = mockMvc.perform(
            MockMvcRequestBuilders.get("/me")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(AuthorityRoleConverter()))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
        Assertions.assertThat(performed.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }
}
