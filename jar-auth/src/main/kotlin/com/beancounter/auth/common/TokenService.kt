package com.beancounter.auth.common

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.server.AuthConstants
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Support for auth tokens.
 */
@Service
class TokenService {
    private var loginService: LoginService? = null

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    val jwtToken: JwtAuthenticationToken?
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
            val jwt = jwtToken
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
            val token = jwtToken ?: return null
            return token.token.subject
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
