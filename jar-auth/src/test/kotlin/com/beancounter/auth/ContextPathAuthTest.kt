package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.WebAuthFilterConfig
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

/**
 * The filter chain as every service deploys it: under the `/api` servlet
 * context path, where Spring Security matches the path inside the context
 * (`/portfolios`), never the `/api` prefix. Each endpoint here has no
 * `@PreAuthorize`, so only the filter-level rules stand between a caller
 * and the controller.
 */
@EnableWebSecurity
@WebMvcTest(
    ContextPathAuthTest.UnguardedController::class,
    properties = ["auth.enabled=true", "server.servlet.context-path=/api"]
)
@ContextConfiguration(
    classes = [
        ContextPathAuthTest.UnguardedController::class,
        WebAuthFilterConfig::class,
        DefaultJWTProcessor::class,
        ContextPathAuthTest.TestCacheConfig::class
    ]
)
class ContextPathAuthTest {
    @TestConfiguration
    class TestCacheConfig {
        @Bean
        fun cacheManager(): CacheManager = ConcurrentMapCacheManager()
    }

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    private fun get(path: String): MockHttpServletRequestBuilder =
        MockMvcRequestBuilders.get("/api$path").contextPath("/api")

    @Test
    fun `should refuse an endpoint without a method guard when no token is sent`() {
        mockMvc.perform(get("/unguarded")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should refuse an unknown path without a token rather than reveal it is missing`() {
        mockMvc.perform(get("/no-such-endpoint")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should refuse an endpoint without a method guard to a token with only the bare beancounter scope`() {
        mockMvc
            .perform(get("/unguarded").with(jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_BC))))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should allow an endpoint without a method guard to a user token`() {
        mockMvc
            .perform(get("/unguarded").with(jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_USER))))
            .andExpect(status().isOk)
    }

    @Test
    fun `should keep the api key token exchange open, since the key itself is the credential`() {
        mockMvc
            .perform(MockMvcRequestBuilders.post("/api/api-keys/token").contextPath("/api"))
            .andExpect(status().isOk)
    }

    @Test
    fun `should keep the published signing keys open`() {
        mockMvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk)
    }

    @Test
    fun `should keep the api docs open`() {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk)
    }

    @RestController
    internal class UnguardedController {
        @GetMapping("/unguarded")
        fun unguarded(): String = "unguarded"

        @PostMapping("/api-keys/token")
        fun token(): String = "token"

        @GetMapping("/.well-known/jwks.json")
        fun jwks(): String = "{}"

        @GetMapping("/swagger-ui/index.html")
        fun swaggerUi(): String = "swagger-ui"
    }
}