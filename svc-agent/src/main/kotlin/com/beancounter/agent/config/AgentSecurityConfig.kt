package com.beancounter.agent.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Security filter chain for the svc-agent endpoints.
 *
 * Declared with a high-precedence `@Order` so it takes priority over jar-auth's
 * catch-all `WebAuthFilterConfig` for the agent paths and the chat UI static
 * resources. The agent is a public-facing service: the chat page and the
 * liveness probe are unauthenticated, while the query endpoint requires a
 * valid JWT so the LLM's tool calls act on behalf of the user.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(value = ["auth.web"], havingValue = "true", matchIfMissing = true)
class AgentSecurityConfig {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun agentSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/", "/agent/**", "/chat.html", "/login.html", "/favicon.ico")
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/", "/chat.html", "/login.html", "/favicon.ico").permitAll()
                auth.requestMatchers("/agent/health").permitAll()
                auth.requestMatchers("/agent/**").authenticated()
            }.csrf { csrf ->
                csrf.disable()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            }

        return http.build()
    }
}