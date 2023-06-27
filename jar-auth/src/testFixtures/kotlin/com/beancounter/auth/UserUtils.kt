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
    fun authUser(
        systemUser: SystemUser,
    ) {
        Mockito.`when`(jwtDecoder.decode(systemUser.email))
            .thenReturn(TokenUtils(authConfig).getUserToken(systemUser))
        val jwt = JwtAuthenticationToken(
            jwtDecoder.decode(
                systemUser.email,
            ),
        )
        SecurityContextHolder.getContext().authentication = jwt

        Mockito.`when`(tokenService.jwt).thenReturn(jwt)
        Mockito.`when`(tokenService.subject)
            .thenReturn(systemUser.email)
    }
}
