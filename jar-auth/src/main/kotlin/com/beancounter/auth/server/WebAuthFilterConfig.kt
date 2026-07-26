package com.beancounter.auth.server

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.OAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration

/**
 * Spring-security config to support OAuth2/JWT for MVC endpoints
 */
@ConditionalOnProperty(
    value = ["auth.web"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true
)
@Import(
    AuthConfig::class,
    OAuthConfig::class,
    TokenService::class
)
@EnableCaching
@Service
@EnableWebSecurity
class WebAuthFilterConfig {
    @Value("\${server.servlet.context-path:/api}")
    private lateinit var apiPath: String

    @Value($$"${management.endpoints.web.base-path:/actuator}")
    private lateinit var actuatorPath: String

    @Value($$"${cors.origins:http://localhost:3000,http://localhost:4000,http://localhost:5000}")
    private lateinit var origins: List<String>

    @Value($$"${cors.origins:Authorization,Cache-Control,Content-Type}")
    private lateinit var headers: List<String>

    @Value("\${cors.exposedHeaders:Authorization}")
    private lateinit var exposedHeaders: List<String>

    @Autowired
    private lateinit var environment: Environment

    private val unrestricted = "unrestricted"

    /**
     * Honour Spring Boot's actuator access model. When
     * `management.endpoints.access.default=unrestricted` the whole actuator
     * surface is anonymous (local profile use); otherwise the ADMIN/SYSTEM
     * rule stands, with per-endpoint `management.endpoint.<id>.access`
     * overrides opening individual endpoints.
     */
    private fun actuatorDefaultUnrestricted(): Boolean =
        unrestricted.equals(
            environment.getProperty("management.endpoints.access.default"),
            ignoreCase = true
        )

    private fun unrestrictedEndpointIds(): List<String> {
        val configurable = environment as? ConfigurableEnvironment ?: return emptyList()
        val accessKey = Regex("management\\.endpoint\\.([a-zA-Z0-9-]+)\\.access")
        return configurable.propertySources
            .filterIsInstance<EnumerablePropertySource<*>>()
            .flatMap { it.propertyNames.asList() }
            .mapNotNull { accessKey.matchEntire(it)?.groupValues?.get(1) }
            .distinct()
            .filter {
                unrestricted.equals(
                    environment.getProperty("management.endpoint.$it.access"),
                    ignoreCase = true
                )
            }
    }

    @Bean
    fun configureBcSecurity(http: HttpSecurity): SecurityFilterChain {
        val corsConfiguration = CorsConfiguration()
        corsConfiguration.allowedHeaders = headers
        corsConfiguration.allowedOrigins = origins
        corsConfiguration.allowedMethods =
            listOf(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PUT",
                "OPTIONS",
                "PATCH",
                "DELETE"
            )
        corsConfiguration.allowCredentials = true

        http
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("$actuatorPath/health/**").permitAll() // Anonymous probing
                auth.requestMatchers("$actuatorPath/openapi/**").permitAll() // API Docs
                auth.requestMatchers("$actuatorPath/swagger-ui/**").permitAll() // API Docs
                auth.requestMatchers("$apiPath/auth").permitAll()
                auth.requestMatchers("$apiPath/docs/**").permitAll()
                auth.requestMatchers("$apiPath/swagger-ui/**").permitAll()
                auth
                    .requestMatchers("$apiPath/**")
                    .hasAnyAuthority(
                        AuthConstants.SCOPE_USER,
                        AuthConstants.SCOPE_SYSTEM,
                        AuthConstants.SCOPE_ADMIN
                    ) // Deny by default: only known, scoped callers
                if (actuatorDefaultUnrestricted()) {
                    auth.requestMatchers("$actuatorPath/**").permitAll()
                } else {
                    unrestrictedEndpointIds().forEach { id ->
                        auth.requestMatchers("$actuatorPath/$id", "$actuatorPath/$id/**").permitAll()
                    }
                    auth
                        .requestMatchers("$actuatorPath/**")
                        .hasAnyAuthority(AuthConstants.SCOPE_ADMIN, AuthConstants.SCOPE_SYSTEM) // Admin or System users
                }
                auth.anyRequest().permitAll() //
            }.csrf { csrf ->
                csrf.disable()
            }.cors { cors ->
                cors.configurationSource { corsConfiguration }
            }.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt(Customizer.withDefaults())
            }
        corsConfiguration.exposedHeaders = exposedHeaders
        return http.build()
    }
}