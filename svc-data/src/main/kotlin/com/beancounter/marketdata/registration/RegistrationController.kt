package com.beancounter.marketdata.registration

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.contracts.UserPreferencesRequest
import com.beancounter.common.exception.ForbiddenException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Once a user is signed up, they need to be registered in order to create portfolios.
 */
@RestController
@RequestMapping("/")
@PreAuthorize("hasAuthority('" + AuthConstants.SCOPE_USER + "')")
class RegistrationController internal constructor(
    private val systemUserService: SystemUserService,
    private val userPreferencesService: UserPreferencesService,
    private val tokenService: TokenService
) {
    @GetMapping("/me")
    fun getMe(): RegistrationResponse {
        val user =
            systemUserService.find(tokenService.subject)
                ?: throw ForbiddenException("Authenticated, but unregistered")
        val preferences = userPreferencesService.getOrCreate(user)
        return RegistrationResponse(user, preferences)
    }

    @PostMapping(value = ["/register"])
    fun register(
        @RequestBody(required = false) registrationRequest: RegistrationRequest
    ): RegistrationResponse {
        val response = systemUserService.register()
        val preferences = userPreferencesService.getOrCreate(response.data)
        return RegistrationResponse(response.data, preferences)
    }

    @PatchMapping("/me")
    fun updatePreferences(
        @RequestBody request: UserPreferencesRequest
    ): RegistrationResponse {
        val user = systemUserService.getOrThrow()
        val preferences = userPreferencesService.update(user, request)
        return RegistrationResponse(user, preferences)
    }
}