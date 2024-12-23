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
class TokenService(
    val authConfig: AuthConfig
) {
    /**
     * Checks if the given authentication is token-based.
     *
     * @param authentication The authentication object to check.
     * @return True if the authentication is token-based, false otherwise.
     */
    private fun isTokenBased(authentication: Authentication): Boolean =
        authentication.javaClass.isAssignableFrom(JwtAuthenticationToken::class.java)

    val jwt: JwtAuthenticationToken
        get() {
            val authentication = SecurityContextHolder.getContext().authentication
            return if (authentication != null && isTokenBased(authentication)) {
                authentication as JwtAuthenticationToken
            } else {
                throw UnauthorizedException("Not authorised")
            }
        }

    fun isGoogle(): Boolean = (subject.startsWith("goog"))

    fun isAuth0(): Boolean = (subject.startsWith("auth0"))

    // M2M token
    private val m2mToken: String
        get() {
            return jwt.token.tokenValue
        }
    val bearerToken: String
        get() = getBearerToken(m2mToken)

    fun getBearerToken(token: String): String = "$BEARER $token"

    val subject: String
        get() {
            val token = jwt
            return token.token.subject
        }

    fun getEmail(): String = jwt.token?.getClaim(authConfig.claimEmail)!!

    fun hasEmail(): Boolean {
        if (jwt.token?.claims?.containsKey(authConfig.claimEmail)!!) {
            val email = jwt.token.claims[authConfig.claimEmail] as String
            return email.isNotBlank()
        }
        return false
    }

    val isServiceToken: Boolean
        get() {
            val permissions = jwt.token.getClaim<List<String>>("permissions")
            val isUser = permissions.contains(AuthConstants.USER)
            val isSystem = permissions.contains(AuthConstants.SYSTEM)
            return isSystem && !isUser
        }

    /**
     * Token Constants
     */
    companion object {
        const val BEARER = "Bearer"
    }
}