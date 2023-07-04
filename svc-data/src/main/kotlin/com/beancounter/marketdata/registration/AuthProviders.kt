package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.common.model.SystemUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

/**
 * Token utils for working with various auth providers.
 * Encapsulate social oriented features here, including hooks and enrichment of systemUser object..
 */
@Service
class AuthProviders(
    private val tokenService: TokenService,
    private val systemUserRepository: SystemUserRepository,
) {
    fun getAuth0Id(jwt: Jwt): String =
        if (tokenService.isAuth0()) jwt.subject else ""

    fun getGoogleId(jwt: Jwt): String =
        if (tokenService.isGoogle()) jwt.subject else ""

    fun capture(result: SystemUser, jwt: Jwt): SystemUser {
        if (tokenService.isGoogle() && result.googleId.isBlank()) {
            // Capture google Id.
            return systemUserRepository.save(
                SystemUser(
                    result.id,
                    result.email,
                    result.active,
                    result.auth0,
                    jwt.subject,
                    result.since,
                ),
            )
        } else if (tokenService.isAuth0() && result.auth0.isBlank()) {
            return systemUserRepository.save(
                SystemUser(
                    result.id,
                    result.email,
                    result.active,
                    jwt.subject,
                    result.googleId,
                    result.since,
                ),
            )
        }
        return result
    }
}
