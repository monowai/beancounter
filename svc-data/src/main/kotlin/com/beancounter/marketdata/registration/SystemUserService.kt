package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.auth.server.Registration
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.model.SystemUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import javax.transaction.Transactional

/**
 * Registration of authenticated users.
 */
@Service
@Transactional
class SystemUserService(
    private val systemUserRepository: SystemUserRepository,
    private val tokenService: TokenService,
) : Registration {
    fun save(systemUser: SystemUser): SystemUser {
        return systemUserRepository.save(systemUser)
    }

    fun isServiceAccount(): Boolean {
        return tokenService.isServiceToken
    }

    fun find(id: String?): SystemUser? {
        if (id == null) return null
        return systemUserRepository.findByAuth0(id).orElse(null)
    }

    override fun register(jwt: Jwt): RegistrationResponse {
        // ToDo: Find by email
        var result = find(jwt.subject)
        if (result == null) {
            if (tokenService.hasEmail()) {
                val systemUser = SystemUser(jwt.subject, tokenService.getEmail())
                result = save(systemUser)
            } else {
                throw BusinessException("Unable to identify your email")
            }
        }
        return RegistrationResponse(result)
    }

    fun getActiveUser(): SystemUser? {
        return find(tokenService.subject)
    }

    fun verifySystemUser(systemUser: SystemUser?) {
        if (systemUser == null) {
            throw ForbiddenException("Unable to identify the owner")
        }
        if (systemUser.id == AuthConstants.SYSTEM) {
            return
        }
        if (!systemUser.active) {
            throw BusinessException("User is not active")
        }
    }

    val getOrThrow: SystemUser
        get() {
            if (isServiceAccount()) {
                return AuthConstants.authSystem
            }
            val systemUser = getActiveUser()
            verifySystemUser(systemUser)
            return systemUser!!
        }
}
