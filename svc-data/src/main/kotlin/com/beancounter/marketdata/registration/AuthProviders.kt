package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.UnauthorizedException
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
    private val systemUserRepository: SystemUserRepository
) {
    fun getAuth0Id(jwt: Jwt): String = if (tokenService.isAuth0()) subjectOf(jwt) else ""

    fun getGoogleId(jwt: Jwt): String = if (tokenService.isGoogle()) subjectOf(jwt) else ""

    fun getAppleId(jwt: Jwt): String = if (tokenService.isApple()) subjectOf(jwt) else ""

    fun capture(
        result: SystemUser,
        jwt: Jwt
    ): SystemUser =
        when {
            tokenService.isGoogle() && result.googleId.isBlank() -> {
                systemUserRepository.save(result.copy(googleId = subjectOf(jwt)))
            }
            tokenService.isAuth0() && result.auth0.isBlank() -> {
                systemUserRepository.save(result.copy(auth0 = subjectOf(jwt)))
            }
            tokenService.isApple() && result.appleId.isBlank() -> {
                systemUserRepository.save(result.copy(appleId = subjectOf(jwt)))
            }
            else -> {
                result
            }
        }

    /**
     * A missing or blank `sub` claim is an authentication failure (401), not a
     * server error (500) — surface it via the unauthorized path.
     */
    private fun subjectOf(jwt: Jwt): String =
        jwt.subject?.takeIf { it.isNotBlank() }
            ?: throw UnauthorizedException("Not authorised: missing subject claim")
}