package com.beancounter.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Service

/**
 * Handles the scenario where you want absolutely no Auth in your config.
 */
@Service
@ConditionalOnProperty(
    "auth.enabled",
    havingValue = "false",
)
@ConditionalOnBean(HttpSecurity::class)
class NoWebAuth {
    @Bean
    fun configure(http: HttpSecurity): SecurityFilterChain =
        http // ...
            .authorizeHttpRequests { auth ->
                auth
                    .anyRequest()
                    .permitAll()
            }.csrf { csrf ->
                csrf.disable()
            }.build()
}
