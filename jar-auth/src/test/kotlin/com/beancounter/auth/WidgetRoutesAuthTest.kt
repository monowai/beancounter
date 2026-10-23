package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.WebAuthFilterConfig
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

/**
 * A widget token (`beancounter:widget` only) reaches the routes named in
 * `auth.widget-routes` and nothing else: the filter chain still denies it by
 * default. Served under the `/api` context path, as every service deploys.
 */
@EnableWebSecurity
@WebMvcTest(
    WidgetRoutesAuthTest.WidgetController::class,
    properties = [
        "auth.enabled=true",
        "server.servlet.context-path=/api",
        "auth.widget-routes[0]=GET /widget-read",
        "auth.widget-routes[1]=POST /widget-query",
        "auth.widget-routes[2]=GET /widget-items/{id}"
    ]
)
@ContextConfiguration(
    classes = [
        WidgetRoutesAuthTest.WidgetController::class,
        WebAuthFilterConfig::class,
        DefaultJWTProcessor::class,
        WidgetRoutesAuthTest.TestCacheConfig::class
    ]
)
class WidgetRoutesAuthTest {
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

    private fun request(
        method: HttpMethod,
        path: String
    ): MockHttpServletRequestBuilder =
        MockMvcRequestBuilders
            .request(method, "/api$path")
            .contextPath("/api")
            .with(jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_WIDGET)))

    @Test
    fun `should allow a widget token on a listed route and method`() {
        mockMvc.perform(request(HttpMethod.GET, "/widget-read")).andExpect(status().isOk)
        mockMvc.perform(request(HttpMethod.POST, "/widget-query")).andExpect(status().isOk)
    }

    @Test
    fun `should allow a widget token on a listed route with a path variable`() {
        mockMvc.perform(request(HttpMethod.GET, "/widget-items/abc")).andExpect(status().isOk)
    }

    @Test
    fun `should refuse a widget token on a listed path with another method`() {
        mockMvc.perform(request(HttpMethod.POST, "/widget-read")).andExpect(status().isForbidden)
        mockMvc.perform(request(HttpMethod.GET, "/widget-query")).andExpect(status().isForbidden)
    }

    @Test
    fun `should refuse a widget token on an unlisted path`() {
        mockMvc.perform(request(HttpMethod.GET, "/unlisted")).andExpect(status().isForbidden)
    }

    @Test
    fun `should still allow a user token on a listed route`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/widget-read")
                    .contextPath("/api")
                    .with(jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_USER)))
            ).andExpect(status().isOk)
    }

    @Test
    fun `should parse a widget route into a method and a path`() {
        assertThat(WebAuthFilterConfig.widgetRoute("POST /prices"))
            .isEqualTo(HttpMethod.POST to "/prices")
        assertThat(WebAuthFilterConfig.widgetRoute("  GET   /portfolios/{id} "))
            .isEqualTo(HttpMethod.GET to "/portfolios/{id}")
    }

    @Test
    fun `should refuse a malformed widget route`() {
        listOf("", "/prices", "GET", "FETCH /prices", "GET prices", "GET /a /b").forEach { entry ->
            assertThatThrownBy { WebAuthFilterConfig.widgetRoute(entry) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `should refuse a wildcard widget route`() {
        listOf("GET /**", "GET /prices/*", "GET /portfolios/**", "GET /price?").forEach { entry ->
            assertThatThrownBy { WebAuthFilterConfig.widgetRoute(entry) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("wildcard")
        }
    }

    @Test
    fun `should refuse a bare root widget route`() {
        assertThatThrownBy { WebAuthFilterConfig.widgetRoute("GET /") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("bare root")
    }

    @RestController
    internal class WidgetController {
        @GetMapping("/widget-read")
        fun read(): String = "read"

        @PostMapping("/widget-read")
        fun writeRead(): String = "write"

        @PostMapping("/widget-query")
        fun query(): String = "query"

        @GetMapping("/widget-query")
        fun getQuery(): String = "query"

        @GetMapping("/widget-items/{id}")
        fun item(
            @PathVariable id: String
        ): String = id

        @GetMapping("/unlisted")
        fun unlisted(): String = "unlisted"
    }
}