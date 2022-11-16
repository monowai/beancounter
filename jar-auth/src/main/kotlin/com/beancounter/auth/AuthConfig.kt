package com.beancounter.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

/**
 * Authentication Configuration.
 */
@Configuration
class AuthConfig {
    @Value("\${auth.audience}")
    val audience: String = "beancounter"

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuer: String = "not-set"

    @Value("\${auth.email}")
    lateinit var claimEmail: String
}
