package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import jakarta.transaction.Transactional
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
        val subject: String? =
            if (tokenService.isAuth0()) {
                tokenService.subject
            } else if (tokenService.isGoogle()) {
                tokenService.subject
            } else {
                null
            }
        val result =
            systemUserCache.find(
                tokenService.getEmail(),
                subject
            )
        val jwt = tokenService.jwt.token
        return if (result == null) {
            RegistrationResponse(
                save(
                    SystemUser(
                        email = tokenService.getEmail(),
                        auth0 = authProviders.getAuth0Id(jwt),
                        googleId = authProviders.getGoogleId(jwt)
                    )
                )
            )
        } else {
            //
            RegistrationResponse(
                authProviders.capture(
                    result,
                    jwt
                )
            )
        }
    }

    fun save(systemUser: SystemUser): SystemUser = systemUserRepository.save(systemUser)

    fun isServiceAccount(): Boolean = tokenService.isServiceToken

    fun find(id: String?): SystemUser? {
        if (id == null) return null
        return systemUserRepository.findByEmail(tokenService.getEmail()).orElse(null)
    }

    fun getActiveUser(): SystemUser? = find(tokenService.subject)

    val getOrThrow: SystemUser
        get() {
            if (isServiceAccount()) {
                return AuthConstants.authSystem
            }
            return getActiveUser()!!
        }

    override fun register(systemUser: SystemUser): RegistrationResponse = RegistrationResponse(save(systemUser))
}