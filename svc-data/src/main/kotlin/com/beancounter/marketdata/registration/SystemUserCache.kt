package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.common.model.SystemUser
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class SystemUserCache(
    val tokenService: TokenService,
    val systemUserRepository: SystemUserRepository
) {
    @Cacheable(
        value = ["system.user"],
        unless = "#result == null"
    )
    fun find(
        email: String?,
        subject: String?
    ): SystemUser? {
        val result =
            when {
                email != null -> systemUserRepository.findByEmail(email).or { findByApple(subject) }
                tokenService.isAuth0() -> systemUserRepository.findByAuth0(subject)
                tokenService.isGoogle() -> systemUserRepository.findByGoogleId(subject)
                else -> findByApple(subject)
            }
        return result.orElse(null)
    }

    // Apple may send its relay address in place of the email the user registered with,
    // so an Apple user is also found by subject when the email finds no one.
    private fun findByApple(subject: String?): Optional<SystemUser> =
        if (subject != null && tokenService.isApple()) {
            systemUserRepository.findByAppleId(subject)
        } else {
            Optional.empty()
        }

    @CacheEvict(
        value = ["system.user"],
        allEntries = true
    )
    fun evictAll() {
        // Cache eviction handled by annotation
    }
}