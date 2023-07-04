package com.beancounter.auth

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.model.SystemUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.Date

/**
 * TestHelper class to generate JWT tokens configured as required.
 */
@Service
class TokenUtils(val authConfig: AuthConfig) {
    fun getSystemUserToken(systemUser: SystemUser): Jwt {
        return getUserToken(systemUser, systemUser.id)
    }

    fun getAuth0Token(systemUser: SystemUser): Jwt {
        return getUserToken(systemUser, systemUser.auth0)
    }

    fun getGoogleToken(systemUser: SystemUser): Jwt {
        return getUserToken(systemUser, systemUser.googleId)
    }

    fun getUserToken(systemUser: SystemUser, subject: String): Jwt {
        return Jwt.withTokenValue(systemUser.id)
            .header("alg", "none")
            .subject(subject)
            .claim(authConfig.claimEmail, systemUser.email)
            .claim("permissions", mutableListOf(AuthConstants.APP_NAME, AuthConstants.USER))
            .claim("scope", AuthConstants.SCOPE)
            .expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()
    }

    fun getSystemToken(systemUser: SystemUser): Jwt {
        return Jwt.withTokenValue(systemUser.id)
            .header("alg", "none")
            .subject(systemUser.id)
            // has to be mutableListOf otherwise becomes ArrayList<Array<String>>
            .claim("permissions", mutableListOf(AuthConstants.APP_NAME, AuthConstants.SYSTEM))
            .claim("scope", AuthConstants.SCOPE)
            .expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()
    }
}
