package com.beancounter.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

/**
 * Authentication Configuration.
 */
@Configuration
class AuthConfig(
    @Value("\${auth.email:\${auth.audience}/claims/email}")
    var claimEmail: String
) {
    @Value("\${auth.audience:https://holdsworth.app}")
    val audience: String = "beancounter"

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuer: String = "not-set"

    @Value("\${spring.security.oauth2.registration.custom.client-id:bc-service}")
    lateinit var clientId: String

    @Value("\${spring.security.oauth2.registration.custom.client-secret:not-set}")
    lateinit var clientSecret: String
}