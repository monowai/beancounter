package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.SystemUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.Date

private const val SCOPE = "scope"

private const val ALG = "alg"

private const val NONE = "none"

/**
 * TestHelper class to generate JWT tokens configured as required.
 */
@Service
class TokenUtils(
    val authConfig: AuthConfig
) {
    fun getSystemUserToken(systemUser: SystemUser): Jwt =
        getUserToken(
            systemUser,
            systemUser.id
        )

    fun getAuth0Token(systemUser: SystemUser): Jwt =
        getUserToken(
            systemUser,
            systemUser.auth0
        )

    fun getGoogleToken(systemUser: SystemUser): Jwt =
        getUserToken(
            systemUser,
            systemUser.googleId
        )

    fun getUserToken(
        systemUser: SystemUser,
        subject: String
    ): Jwt =
        Jwt
            .withTokenValue(systemUser.id)
            .header(
                ALG,
                NONE
            ).subject(subject)
            .claim(
                authConfig.claimEmail,
                systemUser.email
            ).claim(
                SCOPE,
                "${AuthConstants.APP_NAME} ${AuthConstants.USER} ${AuthConstants.ADMIN}"
            ).expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()

    /**
     * M2M (client-credentials) token matching real Auth0 structure.
     * Real M2M tokens have beancounter:system in scope alongside other roles.
     */
    fun getSystemToken(systemUser: SystemUser): Jwt =
        Jwt
            .withTokenValue(systemUser.id)
            .header(
                ALG,
                NONE
            ).subject(systemUser.id)
            .claim(
                SCOPE,
                "${AuthConstants.APP_NAME} ${AuthConstants.SYSTEM} ${AuthConstants.ADMIN}"
            ).expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()

    fun getNoRolesToken(systemUser: SystemUser): Jwt =
        Jwt
            .withTokenValue(systemUser.id)
            .header(
                ALG,
                NONE
            ).subject(systemUser.id)
            .claim(
                SCOPE,
                AuthConstants.APP_NAME
            ).expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()
}