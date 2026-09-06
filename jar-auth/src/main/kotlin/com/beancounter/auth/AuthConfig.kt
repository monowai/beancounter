package com.beancounter.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

/**
 * Authentication Configuration.
 */
@Configuration
class AuthConfig(
    @Value($$"${auth.email:${auth.audience}/claims/email}")
    var claimEmail: String
) {
    @Value($$"${auth.system-user-id:${auth.audience}/claims/system_user_id}")
    var claimSystemUserId: String = ""

    @Value($$"${auth.audience:https://holdsworth.app}")
    val audience: String = "beancounter"

    @Value($$"${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuer: String = "not-set"

    // Optional second trusted JWT issuer, alongside Auth0 - svc-data's own
    // token-exchange endpoint (bc-claude/MCP.md phase 2). Blank (default)
    // means "not configured": OAuthConfig.jwtDecoder then behaves exactly
    // as it did before this issuer existed. When set, it must end with '/'
    // (same convention as `issuer`) since the JWKS URI is derived by simple
    // string concatenation, not URI resolution.
    @Value($$"${auth.bc-issuer.uri:}")
    var bcIssuerUri: String = ""

    @Value($$"${spring.security.oauth2.registration.custom.client-id:bc-service}")
    lateinit var clientId: String

    @Value($$"${spring.security.oauth2.registration.custom.client-secret:not-set}")
    lateinit var clientSecret: String

    @Value($$"${auth.jwks.connect-timeout:10}")
    val jwksConnectTimeout: Long = 10

    @Value($$"${auth.jwks.read-timeout:10}")
    val jwksReadTimeout: Long = 10

    // JWKS cache configuration - Auth0 keys rarely change, so long cache is safe
    // Default: 24 hours cache lifespan, refresh 1 hour before expiry
    @Value($$"${auth.jwks.cache-lifespan-hours:24}")
    val jwksCacheLifespanHours: Long = 24

    @Value($$"${auth.jwks.cache-refresh-ahead-hours:1}")
    val jwksCacheRefreshAheadHours: Long = 1
}