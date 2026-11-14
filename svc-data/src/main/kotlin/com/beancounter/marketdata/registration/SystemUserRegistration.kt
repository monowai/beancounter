package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

/**
 * Registration of authenticated users: sign-up, reactivation of an offboarded
 * account, and service accounts. Resolving the caller lives in [SystemUserService].
 */
@Service
@Transactional
class SystemUserRegistration(
    private val systemUserRepository: SystemUserRepository,
    private val tokenService: TokenService,
    private val authProviders: AuthProviders,
    private val systemUserCache: SystemUserCache
) : Registration {
    fun registerSystemAccount(id: String): RegistrationResponse {
        if (!tokenService.isServiceToken) {
            throw BusinessException("Only Service accounts can be registered here")
        }

        return RegistrationResponse(
            save(
                SystemUser(
                    id = id,
                    email = id
                )
            )
        )
    }

    fun register(): RegistrationResponse {
        if (tokenService.isServiceToken) {
            throw BusinessException("Service accounts can not be registered for user activities")
        }

        if (!tokenService.hasEmail()) {
            throw BusinessException(
                "No email. This token cannot be registered for user activities."
            )
        }
        val email = tokenService.getEmail()
        val subject: String? =
            if (tokenService.isAuth0() || tokenService.isGoogle() || tokenService.isApple()) {
                tokenService.subject
            } else {
                null
            }
        val result = systemUserCache.find(email, subject)
        val jwt = tokenService.jwt.token
        return if (result == null) {
            RegistrationResponse(signUp(email, jwt))
        } else {
            // Existing user. If they previously offboarded (soft-deleted), signing
            // up again reactivates the same account rather than orphaning them.
            RegistrationResponse(
                authProviders.capture(
                    reactivate(result),
                    jwt
                )
            )
        }
    }

    private fun signUp(
        email: String,
        jwt: Jwt
    ): SystemUser =
        try {
            save(
                SystemUser(
                    email = email,
                    auth0 = authProviders.getAuth0Id(jwt),
                    googleId = authProviders.getGoogleId(jwt),
                    appleId = authProviders.getAppleId(jwt)
                )
            )
        } catch (_: DataIntegrityViolationException) {
            // Race condition - another request created it first
            log.info("User $email already registered (race condition), fetching existing record")
            val existing =
                systemUserRepository.findByEmail(email).orElseThrow {
                    IllegalStateException("Failed to get or create user for $email")
                }
            authProviders.capture(reactivate(existing), jwt)
        }

    /**
     * Re-enable a previously offboarded (deactivated) user on re-registration.
     * No-op for an already-active user.
     */
    private fun reactivate(user: SystemUser): SystemUser =
        if (user.active) {
            user
        } else {
            log.info("Reactivating offboarded user {}", user.id)
            save(user.apply { active = true })
        }

    fun save(systemUser: SystemUser): SystemUser = systemUserRepository.save(systemUser)

    override fun register(systemUser: SystemUser): RegistrationResponse = RegistrationResponse(save(systemUser))

    companion object {
        private val log = LoggerFactory.getLogger(SystemUserRegistration::class.java)
    }
}