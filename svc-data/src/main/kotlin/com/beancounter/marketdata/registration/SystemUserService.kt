package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.model.SystemUser
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * Resolves the caller, or another user by id, email or external subject, to a
 * [SystemUser]. Sign-up and reactivation live in [SystemUserRegistration].
 */
@Service
@Transactional
class SystemUserService(
    private val systemUserRepository: SystemUserRepository,
    private val tokenService: TokenService,
    private val systemUserCache: SystemUserCache
) {
    companion object {
        const val USER_NOT_AUTHENTICATED = "User not authenticated"
    }

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
        val byEmail = if (email.isNullOrBlank()) null else systemUserRepository.findByEmail(email).orElse(null)
        return byEmail ?: findBySubject(sub)
    }

    private fun findBySubject(sub: String?): SystemUser? =
        when {
            sub.isNullOrBlank() -> null
            sub.startsWith("auth0") -> systemUserRepository.findByAuth0(sub).orElse(null)
            sub.startsWith("goog") -> systemUserRepository.findByGoogleId(sub).orElse(null)
            else -> null
        }

    fun findByEmail(email: String): SystemUser? = systemUserRepository.findByEmail(email).orElse(null)

    /**
     * Find a SystemUser by its primary key (SystemUser.id). Used by
     * service-to-service callers that pass an owner identifier in their
     * request (e.g. svc-retire projecting a shared INDEPENDENCE_PLAN).
     */
    fun findById(id: String): SystemUser? = systemUserRepository.findById(id).orElse(null)

    /**
     * Resolve the caller's SystemUser. By default an offboarded (deactivated)
     * user resolves to null so their session is effectively dead — protected
     * endpoints get [ForbiddenException] via [getOrThrow] and the UI forces a
     * logout. Pass [includeInactive] = true only where a deactivated user must
     * still be found, e.g. the /register reactivation path.
     */
    fun getActiveUser(includeInactive: Boolean = false): SystemUser? {
        val user = find(tokenService.subject) ?: return null
        return if (includeInactive || user.active) user else null
    }

    fun getOrThrow(): SystemUser {
        if (isServiceAccount()) {
            return AuthConstants.authSystem
        }
        return getActiveUser()
            ?: throw ForbiddenException("User is authenticated but not registered. Please call /register first.")
    }
}