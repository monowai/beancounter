package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.WebAuthFilterConfig
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

/**
 * Verifies WebAuthFilterConfig honours Spring Boot's actuator access
 * properties (`management.endpoints.access.default` and per-endpoint
 * `management.endpoint.<id>.access`) so application-local.yml can open
 * actuator endpoints without code changes. Default remains secured:
 * ADMIN/SYSTEM scope for everything except health/openapi/swagger.
 */
@RestController
class FakeActuatorController {
    @GetMapping("/actuator/health")
    fun health(): String = "UP"

    @GetMapping("/actuator/env")
    fun env(): String = "env"

    @GetMapping("/actuator/loggers")
    fun loggers(): String = "loggers"
}

@TestConfiguration
class ActuatorTestCacheConfig {
    @Bean
    fun cacheManager(): CacheManager = ConcurrentMapCacheManager()
}

abstract class ActuatorAccessBase {
    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var context: WebApplicationContext

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    protected fun adminJwt() = jwt().authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_ADMIN))
}

@EnableWebSecurity
@WebMvcTest(FakeActuatorController::class, properties = ["auth.enabled=true"])
@org.springframework.test.context.ContextConfiguration(
    classes = [FakeActuatorController::class, WebAuthFilterConfig::class, ActuatorTestCacheConfig::class]
)
class ActuatorSecuredByDefaultTest : ActuatorAccessBase() {
    @Test
    fun `health is anonymous by default`() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }

    @Test
    fun `actuator endpoints require a scoped token by default`() {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/actuator/env").with(adminJwt())).andExpect(status().isOk)
    }
}

@EnableWebSecurity
@WebMvcTest(
    FakeActuatorController::class,
    properties = ["auth.enabled=true", "management.endpoints.access.default=unrestricted"]
)
@org.springframework.test.context.ContextConfiguration(
    classes = [FakeActuatorController::class, WebAuthFilterConfig::class, ActuatorTestCacheConfig::class]
)
class ActuatorDefaultUnrestrictedTest : ActuatorAccessBase() {
    @Test
    fun `access default unrestricted opens all actuator endpoints anonymously`() {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/loggers")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }
}

@EnableWebSecurity
@WebMvcTest(
    FakeActuatorController::class,
    properties = ["auth.enabled=true", "management.endpoint.env.access=unrestricted"]
)
@org.springframework.test.context.ContextConfiguration(
    classes = [FakeActuatorController::class, WebAuthFilterConfig::class, ActuatorTestCacheConfig::class]
)
class ActuatorPerEndpointUnrestrictedTest : ActuatorAccessBase() {
    @Test
    fun `unrestricted endpoint is anonymous while the rest stay secured`() {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/loggers")).andExpect(status().isUnauthorized)
    }
}