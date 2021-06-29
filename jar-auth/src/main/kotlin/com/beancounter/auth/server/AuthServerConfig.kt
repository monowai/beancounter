package com.beancounter.auth.server

import com.beancounter.auth.common.TokenService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@Configuration
@Import(TokenService::class)
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(value = ["auth.enabled"], matchIfMissing = true)
/**
 * WebSecurity Authentication
 */
class AuthServerConfig
