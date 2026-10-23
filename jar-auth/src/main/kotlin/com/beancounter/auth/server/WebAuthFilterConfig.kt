package com.beancounter.auth.server

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.OAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
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
    @Value($$"${management.endpoints.web.base-path:/actuator}")
    private lateinit var actuatorPath: String

    @Value($$"${cors.origins:http://localhost:3000,http://localhost:4000,http://localhost:5000}")
    private lateinit var origins: List<String>

    @Value($$"${cors.origins:Authorization,Cache-Control,Content-Type}")
    private lateinit var headers: List<String>

    @Value($$"${cors.exposedHeaders:Authorization}")
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
                // Matchers see the path inside the servlet context: a request for
                // /api/auth arrives as /auth. Prefixing them with the context path
                // meant none matched, and every request fell through to permitAll.
                auth.requestMatchers("/auth").permitAll()
                // BC API-key token exchange (bc-claude/MCP.md phase 2): authenticates
                // by the key itself, not a user JWT - can't sit behind the JWT gate.
                auth.requestMatchers("/api-keys/token").permitAll()
                // JWKS is public key material by design - every trusted issuer
                // (Auth0, and now svc-data's own bc-issuer) publishes it openly.
                auth.requestMatchers("/.well-known/**").permitAll()
                auth.requestMatchers("/docs/**").permitAll()
                auth.requestMatchers("/swagger-ui/**").permitAll()
                // Boot's error page reports the failure of a request already let through.
                auth.requestMatchers("/error").permitAll()
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
                // Widget tokens work without biometrics, so they reach only the read routes
                // this service lists in auth.widget-routes. Each handler still narrows the
                // scope with its own @PreAuthorize.
                widgetRoutes().forEach { (method, path) ->
                    auth
                        .requestMatchers(method, path)
                        .hasAnyAuthority(
                            AuthConstants.SCOPE_WIDGET,
                            AuthConstants.SCOPE_USER,
                            AuthConstants.SCOPE_SYSTEM,
                            AuthConstants.SCOPE_ADMIN
                        )
                }
                // Deny by default: only known, scoped callers reach a controller,
                // so one that forgets @PreAuthorize is not open to anyone.
                auth
                    .anyRequest()
                    .hasAnyAuthority(
                        AuthConstants.SCOPE_USER,
                        AuthConstants.SCOPE_SYSTEM,
                        AuthConstants.SCOPE_ADMIN
                    )
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

    /**
     * `auth.widget-routes`: the read routes a widget token may reach, as `"<METHOD> <path>"` in
     * servlet-context terms (`GET /net-worth`, not `/api/net-worth`). Bound with [Binder] so a
     * YAML list and a comma-separated value both work.
     */
    private fun widgetRoutes(): List<Pair<HttpMethod, String>> =
        Binder
            .get(environment)
            .bind("auth.widget-routes", Bindable.listOf(String::class.java))
            .orElse(emptyList())
            .orEmpty()
            .map { widgetRoute(it) }

    companion object {
        private val HTTP_METHODS =
            setOf(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE
            )

        /**
         * Parses one `auth.widget-routes` entry. A malformed entry throws, so a typo fails
         * startup instead of silently leaving a route closed or open.
         */
        fun widgetRoute(entry: String): Pair<HttpMethod, String> {
            val parts = entry.trim().split(Regex("\\s+"))
            require(parts.size == 2) { "auth.widget-routes entry must be '<METHOD> <path>': '$entry'" }
            val method = HTTP_METHODS.firstOrNull { it.name() == parts[0].uppercase() }
            requireNotNull(method) { "auth.widget-routes entry has an unknown method: '$entry'" }
            require(parts[1].startsWith("/")) { "auth.widget-routes path must start with '/': '$entry'" }
            return method to parts[1]
        }
    }
}