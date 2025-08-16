package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.WebAuthFilterConfig
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test suite for OAuth authentication controller to ensure proper authorization behavior.
 *
 * This class tests:
 * - Unauthorized access handling (no token scenarios)
 * - Authorized access with proper scopes
 * - Access denial for insufficient permissions
 * - OAuth token validation and processing
 *
 * Tests use Spring Security's MockMvc to simulate HTTP requests with various
 * authentication states and verify the appropriate responses.
 */
@ExtendWith(SpringExtension::class)
@EnableWebSecurity
@WebMvcTest(
    AuthTest.SimpleController::class,
    properties = ["auth.enabled=true"]
)
@ContextConfiguration(
    classes = [
        AuthTest.SimpleController::class,
        WebAuthFilterConfig::class,
        DefaultJWTProcessor::class
    ]
)
class AuthTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    /**
     * Constants for the tests.
     */
    companion object {
        private const val HELLO = "/api/hello"
        const val WHAT = "/api/what"
    }

    @Test
    fun `should return unauthorized when no token is provided`() {
        var result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(HELLO)
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(WHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    @WithMockUser(
        username = "testUser",
        authorities = [AuthConstants.SCOPE_BC, AuthConstants.SCOPE_USER]
    )
    fun `should allow access when user has proper authority`() {
        mockMvc
            .perform(MockMvcRequestBuilders.get(HELLO))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
    }

    @Test
    @WithMockUser(
        username = "testUser",
        authorities = [AuthConstants.SCOPE_USER]
    )
    fun `should deny access when user lacks required authority`() {
        mockMvc
            .perform(MockMvcRequestBuilders.get(WHAT))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    @WithMockUser(
        username = "testUser",
        authorities = ["no-valid-auth"]
    )
    fun has_tokenButNoRoleToSayAnything() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(HELLO)
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()

        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(WHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isForbidden)
                .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
    }

    @Test
    fun swaggerUi_IsAccessibleWithoutAuthentication() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/swagger-ui/index.html")
                        .contentType(MediaType.APPLICATION_JSON)
                ).andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.OK.value())
    }

    @RestController
    internal class SimpleController {
        @GetMapping(HELLO)
        @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_USER}')")
        fun sayHello(): String = "hello"

        @GetMapping(WHAT)
        @PreAuthorize("hasAuthority('${AuthConstants.ADMIN}')")
        fun sayWhat(): String = "no one can call this"

        @GetMapping("/api/swagger-ui/index.html")
        fun swaggerUi(): String = "swagger-ui"
    }
}