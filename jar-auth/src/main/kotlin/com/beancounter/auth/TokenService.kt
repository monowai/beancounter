package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.AuthConstants
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Support to answer various questions around JWT tokens.
 */
@Service
class TokenService(val loginService: LoginService?, val authConfig: AuthConfig) {

    val jwt: JwtAuthenticationToken?
        get() {
            val authentication = SecurityContextHolder.getContext().authentication
                ?: return null
            return if (isTokenBased(authentication)) {
                authentication as JwtAuthenticationToken
            } else {
                null
            }
        }

    // M2M token
    val token: String?
        get() {
            val jwt = jwt
            if (jwt != null) {
                return jwt.token.tokenValue
            } else {
                if (loginService != null) {
                    // M2M token
                    return loginService!!.login()
                }
            }
            return null
        }
    val bearerToken: String
        get() = getBearerToken(token)

    fun getBearerToken(token: String?): String {
        return BEARER + token
    }

    val subject: String?
        get() {
            val token = jwt ?: return null
            return token.token.subject
        }

    fun getEmail(): String {
        return jwt?.token?.getClaim(authConfig.claimEmail)!!
    }

    fun hasEmail(): Boolean {
        return jwt?.token?.claims?.containsKey(authConfig.claimEmail) ?: false
    }

    val isServiceToken: Boolean
        get() {
            val authentication = SecurityContextHolder.getContext().authentication
                ?: return false
            return authentication.authorities.contains(AuthConstants.AUTH_M2M)
        }

    companion object {
        const val BEARER = "Bearer "
        private fun isTokenBased(authentication: Authentication): Boolean {
            return authentication.javaClass.isAssignableFrom(JwtAuthenticationToken::class.java)
        }
    }
}
