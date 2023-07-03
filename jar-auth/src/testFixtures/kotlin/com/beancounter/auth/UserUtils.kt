package com.beancounter.auth

import com.beancounter.common.model.SystemUser
import org.mockito.Mockito
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Mock out Beancounter user expectations for authentication.
 */
@Service
class UserUtils(val authConfig: AuthConfig, val jwtDecoder: JwtDecoder, val tokenService: TokenService) {
    private val tokenUtils = TokenUtils(authConfig)

    /**
     * Known providers id's that we will track.
     */
    enum class AuthProvider {
        ID, GOOGLE, AUTH0
    }

    fun authenticate(
        systemUser: SystemUser,
        authProvider: AuthProvider = AuthProvider.ID,
    ): JwtAuthenticationToken {
        Mockito.`when`(jwtDecoder.decode(systemUser.email))
            .thenReturn(
                when (authProvider) {
                    AuthProvider.GOOGLE -> tokenUtils.getGoogleToken(systemUser)
                    AuthProvider.AUTH0 -> tokenUtils.getAuth0Token(
                        systemUser,
                    )

                    else -> tokenUtils.getSystemUserToken(
                        systemUser,
                    )
                },
            )
        val jwt = JwtAuthenticationToken(
            jwtDecoder.decode(
                systemUser.email,
            ),
        )
        SecurityContextHolder.getContext().authentication = jwt

        return jwt
    }
}
