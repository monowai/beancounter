package com.beancounter.auth.server

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.OAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
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

    @Value("\${management.endpoints.web.base-path:/actuator}")
    private lateinit var actuatorPath: String

    @Value("\${cors.origins:http://localhost:3000,http://localhost:4000,http://localhost:5000}")
    private lateinit var origins: List<String>

    @Value("\${cors.origins:Authorization,Cache-Control,Content-Type}")
    private lateinit var headers: List<String>

    @Value("\${cors.exposedHeaders:Authorization}")
    private lateinit var exposedHeaders: List<String>

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
                auth.requestMatchers("$apiPath/auth").permitAll() // Get your token
                auth
                    .requestMatchers("$apiPath/**")
                    .hasAuthority(AuthConstants.SCOPE_BC) // Authenticated users
                auth
                    .requestMatchers("$actuatorPath/**")
                    .hasAuthority(AuthConstants.SCOPE_ADMIN) // Admin users
//            auth.requestMatchers("$actuatorPath/**").hasRole(AuthConstants.ADMIN) // Admin users
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