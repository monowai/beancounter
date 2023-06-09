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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration

/**
 * Spring-security config to support OAuth2/JWT for MVC endpoints
 */
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import(AuthConfig::class, OAuthConfig::class, TokenService::class)
@ConditionalOnProperty(value = ["auth.web"], havingValue = "true", matchIfMissing = true)
@EnableCaching
@Service
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
            listOf("GET", "POST", "PUT", "DELETE", "PUT", "OPTIONS", "PATCH", "DELETE")
        corsConfiguration.allowCredentials = true

        http.authorizeHttpRequests()
            .requestMatchers("$actuatorPath/health/**").permitAll() // Anonymous probing
            .requestMatchers("$apiPath/auth").permitAll() // Get your token
            .requestMatchers("$apiPath/**").hasAuthority(AuthConstants.SCOPE_BC) // Authenticated users
            .requestMatchers("$actuatorPath/**").hasAuthority(AuthConstants.SCOPE_ADMIN) // Admin users
            .anyRequest().permitAll() //
            .and().csrf().disable().cors().configurationSource { corsConfiguration }
            .and().oauth2ResourceServer()
            .jwt() // User roles are carried in the claims and used for fine-grained control

        corsConfiguration.exposedHeaders = exposedHeaders
        return http.build()
    }
}
