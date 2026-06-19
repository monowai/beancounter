package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.WebAuthFilterConfig
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

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
        DefaultJWTProcessor::class,
        AuthTest.TestCacheConfig::class
    ]
)
class AuthTest {
    /**
     * WebAuthFilterConfig is annotated @EnableCaching but does not declare a
     * CacheManager (production supplies one). The sliced test context has none,
     * so provide a simple in-memory manager here.
     */
    @org.springframework.boot.test.context.TestConfiguration
    class TestCacheConfig {
        @org.springframework.context.annotation.Bean
        fun cacheManager(): org.springframework.cache.CacheManager =
            org.springframework.cache.concurrent
                .ConcurrentMapCacheManager()
    }

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        // Build MockMvc with the Spring Security filter chain so the jwt() request
        // post-processor's authentication is honoured by the resource-server chain.
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

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
    fun `should allow access when user has proper authority`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(HELLO)
                    .with(
                        jwt().authorities(
                            SimpleGrantedAuthority(AuthConstants.SCOPE_BC),
                            SimpleGrantedAuthority(AuthConstants.SCOPE_USER)
                        )
                    )
            ).andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
    }

    @Test
    fun `should deny access when user lacks required authority`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(WHAT)
                    .with(jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_USER)))
            ).andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    fun has_tokenButNoRoleToSayAnything() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(HELLO)
                    .with(jwt().authorities(SimpleGrantedAuthority("no-valid-auth")))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()

        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(WHAT)
                        .with(jwt().authorities(SimpleGrantedAuthority("no-valid-auth")))
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
        companion object {
            private const val HELLO_RESPONSE = "hello"
            private const val WHAT_RESPONSE = "no one can call this"
            private const val SWAGGER_UI_RESPONSE = "swagger-ui"
        }

        @GetMapping(HELLO)
        @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_USER}')")
        fun sayHello(): String = HELLO_RESPONSE

        @GetMapping(WHAT)
        @PreAuthorize("hasAuthority('${AuthConstants.ADMIN}')")
        fun sayWhat(): String = WHAT_RESPONSE

        @GetMapping("/api/swagger-ui/index.html")
        fun swaggerUi(): String = SWAGGER_UI_RESPONSE
    }
}