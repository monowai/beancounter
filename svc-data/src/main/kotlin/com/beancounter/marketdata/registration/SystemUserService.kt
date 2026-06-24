package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.model.SystemUser
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * Registration of authenticated users.
 */
@Service
@Transactional
class SystemUserService(
    private val systemUserRepository: SystemUserRepository,
    private val tokenService: TokenService,
    private val authProviders: AuthProviders,
    private val systemUserCache: SystemUserCache
) : Registration {
    fun registerSystemAccount(id: String): RegistrationResponse {
        if (!isServiceAccount()) {
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
        if (isServiceAccount()) {
            throw BusinessException("Service accounts can not be registered for user activities")
        }

        if (!tokenService.hasEmail()) {
            throw BusinessException(
                "No email. This token cannot be registered for user activities."
            )
        }
        val email = tokenService.getEmail()
        val subject: String? =
            if (tokenService.isAuth0()) {
                tokenService.subject
            } else if (tokenService.isGoogle()) {
                tokenService.subject
            } else {
                null
            }
        val result = systemUserCache.find(email, subject)
        val jwt = tokenService.jwt.token
        return if (result == null) {
            try {
                RegistrationResponse(
                    save(
                        SystemUser(
                            email = email,
                            auth0 = authProviders.getAuth0Id(jwt),
                            googleId = authProviders.getGoogleId(jwt)
                        )
                    )
                )
            } catch (_: DataIntegrityViolationException) {
                // Race condition - another request created it first
                log.info("User $email already registered (race condition), fetching existing record")
                val existing =
                    systemUserRepository.findByEmail(email).orElseThrow {
                        IllegalStateException("Failed to get or create user for $email")
                    }
                RegistrationResponse(authProviders.capture(reactivate(existing), jwt))
            }
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

    companion object {
        const val USER_NOT_AUTHENTICATED = "User not authenticated"
        private val log = LoggerFactory.getLogger(SystemUserService::class.java)
    }

    fun save(systemUser: SystemUser): SystemUser = systemUserRepository.save(systemUser)

    fun isServiceAccount(): Boolean = tokenService.isServiceToken

    fun find(id: String?): SystemUser? {
        if (id == null) return null
        val email = if (tokenService.hasEmail()) tokenService.getEmail() else null
        return systemUserCache.find(email, id)
    }

    fun findByExternal(
        sub: String?,
        email: String?
    ): SystemUser? {
        if (!email.isNullOrBlank()) {
            val byEmail = systemUserRepository.findByEmail(email).orElse(null)
            if (byEmail != null) return byEmail
        }
        if (!sub.isNullOrBlank()) {
            return when {
                sub.startsWith("auth0") -> systemUserRepository.findByAuth0(sub).orElse(null)
                sub.startsWith("goog") -> systemUserRepository.findByGoogleId(sub).orElse(null)
                else -> null
            }
        }
        return null
    }

    fun findByEmail(email: String): SystemUser? = systemUserRepository.findByEmail(email).orElse(null)

    /**
     * Find a SystemUser by its primary key (SystemUser.id). Used by
     * service-to-service callers that pass an owner identifier in their
     * request (e.g. svc-retire projecting a shared INDEPENDENCE_PLAN).
     */
    fun findById(id: String): SystemUser? = systemUserRepository.findById(id).orElse(null)

    fun getActiveUser(): SystemUser? = find(tokenService.subject)

    fun getOrThrow(): SystemUser {
        if (isServiceAccount()) {
            return AuthConstants.authSystem
        }
        return getActiveUser()
            ?: throw ForbiddenException("User is authenticated but not registered. Please call /register first.")
    }

    override fun register(systemUser: SystemUser): RegistrationResponse = RegistrationResponse(save(systemUser))
}