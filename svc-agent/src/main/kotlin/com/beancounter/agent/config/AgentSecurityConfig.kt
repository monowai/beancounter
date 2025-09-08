package com.beancounter.agent.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Custom security configuration for the agent service.
 * Handles only agent-specific endpoints, allowing chat interface without authentication.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(value = ["auth.web"], havingValue = "true", matchIfMissing = true)
class AgentSecurityConfig {
    @Bean
    fun agentSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/", "/api/agent/**") // Handle root path and agent endpoints
            .authorizeHttpRequests { auth ->
                // Allow access to root path (chat interface) without authentication
                auth.requestMatchers("/").permitAll()

                // Allow access to chat interface and login without authentication
                auth.requestMatchers("/api/agent/chat").permitAll()
                auth.requestMatchers("/api/agent/login").permitAll()
                auth.requestMatchers("/api/agent/health").permitAll()

                // Require authentication for other agent endpoints
                auth.requestMatchers("/api/agent/**").authenticated()
            }.csrf { csrf ->
                csrf.disable()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            }

        return http.build()
    }
}