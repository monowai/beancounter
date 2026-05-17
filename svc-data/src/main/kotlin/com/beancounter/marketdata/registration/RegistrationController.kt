package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.contracts.UserPreferencesRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Once a user is signed up, they need to be registered in order to create portfolios.
 */
@RestController
@RequestMapping("/")
class RegistrationController internal constructor(
    private val systemUserService: SystemUserService,
    private val userPreferencesService: UserPreferencesService,
    private val tokenService: TokenService
) {
    @GetMapping("/me")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
    )
    fun getMe(
        @RequestParam(required = false) sub: String?,
        @RequestParam(required = false) email: String?
    ): RegistrationResponse {
        if (tokenService.isServiceToken) {
            if (sub.isNullOrBlank() && email.isNullOrBlank()) {
                throw BusinessException("Service callers must supply 'sub' or 'email'")
            }
            val target =
                systemUserService.findByExternal(sub, email)
                    ?: throw NotFoundException("No SystemUser for the supplied sub/email")
            val preferences = userPreferencesService.getOrCreate(target)
            return RegistrationResponse(target, preferences)
        }

        if (!sub.isNullOrBlank() || !email.isNullOrBlank()) {
            throw BusinessException("User callers must not supply 'sub' or 'email'")
        }
        val user =
            systemUserService.find(tokenService.subject)
                ?: throw ForbiddenException("Authenticated, but unregistered")
        val preferences = userPreferencesService.getOrCreate(user)
        return RegistrationResponse(user, preferences)
    }

    @PostMapping(value = ["/register"])
    @PreAuthorize("hasAuthority('" + AuthConstants.SCOPE_USER + "')")
    fun register(
        @RequestBody(required = false) registrationRequest: RegistrationRequest
    ): RegistrationResponse {
        val response = systemUserService.register()
        val preferences = userPreferencesService.getOrCreate(response.data)
        return RegistrationResponse(response.data, preferences)
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('" + AuthConstants.SCOPE_USER + "')")
    fun updatePreferences(
        @RequestBody request: UserPreferencesRequest
    ): RegistrationResponse {
        val user = systemUserService.getOrThrow()
        val preferences = userPreferencesService.update(user, request)
        return RegistrationResponse(user, preferences)
    }
}