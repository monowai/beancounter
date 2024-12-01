package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.common.model.SystemUser
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class SystemUserCache(val tokenService: TokenService, val systemUserRepository: SystemUserRepository) {
    @Cacheable(value = ["system.user"], unless = "#result == null")
    fun find(
        email: String?,
        subject: String?,
    ): SystemUser? {
        val result =
            when {
                email != null -> systemUserRepository.findByEmail(email)
                tokenService.isAuth0() -> systemUserRepository.findByAuth0(subject!!)
                tokenService.isGoogle() -> systemUserRepository.findByGoogleId(subject!!)
                else -> Optional.ofNullable(null)
            }
        return result.orElse(null)
    }
}
