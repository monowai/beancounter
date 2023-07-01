package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.exception.UnauthorizedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Support to answer various questions around JWT tokens.
 */
@Service
class TokenService(val authConfig: AuthConfig) {

    val jwt: JwtAuthenticationToken
        get() {
            val authentication = SecurityContextHolder.getContext().authentication
            return if (authentication != null && isTokenBased(authentication)) {
                authentication as JwtAuthenticationToken
            } else {
                throw UnauthorizedException("Not authorised")
            }
        }

    fun isGoogle(): Boolean {
        return (subject.startsWith("goog"))
    }

    fun isAuth0(): Boolean {
        return (subject.startsWith("auth0"))
    }

    // M2M token
    val m2mToken: String
        get() {
            val jwt = jwt
            return jwt.token.tokenValue
        }
    val bearerToken: String
        get() = getBearerToken(m2mToken)

    fun getBearerToken(token: String): String {
        return "$BEARER $token"
    }

    val subject: String
        get() {
            val token = jwt
            return token.token.subject
        }

    fun getEmail(): String {
        return jwt.token?.getClaim(authConfig.claimEmail)!!
    }

    fun hasEmail(): Boolean {
        return jwt.token?.claims?.containsKey(authConfig.claimEmail) ?: false
    }

    val isServiceToken: Boolean
        get() {
            val authentication = SecurityContextHolder.getContext().authentication
                ?: return false
            return authentication.authorities.contains(AuthConstants.AUTH_M2M)
        }

    companion object {
        const val BEARER = "Bearer"
        private fun isTokenBased(authentication: Authentication): Boolean {
            return authentication.javaClass.isAssignableFrom(JwtAuthenticationToken::class.java)
        }
    }
}
