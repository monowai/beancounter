package com.beancounter.auth.server

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * No-auth security profile used only for local development and tests
 * (`auth.web=false`). Permits all requests and disables CSRF — both
 * intentional, because there is no session to forge or protect when
 * authentication is turned off entirely.
 */
@Configuration
@ConditionalOnProperty(value = ["auth.web"], havingValue = "false", matchIfMissing = false)
class NoAuthSecurityConfig {
    @Bean
    fun permitAllSecurity(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            // deepcode ignore DisablesCSRFProtection: No-auth profile for
            // local dev / tests. CSRF protection is meaningless when there
            // is no session to forge.
            .csrf { it.disable() }
        return http.build()
    }
}