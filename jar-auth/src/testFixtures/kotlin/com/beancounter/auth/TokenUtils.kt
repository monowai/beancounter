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
    fun getUserToken(systemUser: SystemUser): Jwt {
        return Jwt.withTokenValue(systemUser.id)
            .header("alg", "none")
            .subject(systemUser.id)
            .claim(authConfig.claimEmail, systemUser.email)
            .claim("permissions", arrayOf(AuthConstants.APP_NAME, AuthConstants.USER))
            .claim("scope", AuthConstants.SCOPE)
            .expiresAt(Date(System.currentTimeMillis() + 60000).toInstant())
            .build()
    }
}
