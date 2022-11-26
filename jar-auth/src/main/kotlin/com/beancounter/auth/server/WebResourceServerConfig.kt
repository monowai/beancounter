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
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration

/**
 * Spring-security config to support OAuth2/JWT for MVC endpoints
 */
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import(AuthConfig::class, OAuthConfig::class, TokenService::class)
@ConditionalOnProperty(value = ["auth.enabled"], havingValue = "true", matchIfMissing = true)
@EnableWebSecurity
@EnableCaching
@Service
class WebResourceServerConfig {
    @Value("\${auth.pattern:/**}")
    private lateinit var apiPattern: String // All these EPs are by default secured

    @Value("\${management.server.base-path:/management}")
    private lateinit var actuatorPattern: String

    @Value("\${cors.origins:http://localhost:3000,http://localhost:4000}")
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
            listOf("GET", "POST", "PUT", "DELETE", "PUT", "OPTIONS", "PATCH", "DELETE")
        corsConfiguration.allowCredentials = true
        http.authorizeRequests() // Scope permits access to the API - basically, "caller is authorised"
            .mvcMatchers(
                "$actuatorPattern/actuator/health/ping",
                "$actuatorPattern/actuator/health/livenessState",
                "$actuatorPattern/actuator/health/readinessState"
            ).permitAll()
            .mvcMatchers("$actuatorPattern/actuator/**")
            .hasAuthority(AuthConstants.SCOPE_ADMIN)
            .mvcMatchers(apiPattern)
            .hasAuthority(AuthConstants.SCOPE_BC)
            .anyRequest().authenticated()
            .and().csrf().disable().cors().configurationSource { corsConfiguration }
            .and().oauth2ResourceServer()
            .jwt() // User roles are carried in the claims and used for fine-grained control
        corsConfiguration.exposedHeaders = exposedHeaders
        return http.build()
    }
}
