package com.beancounter.marketdata.registration

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.ForbiddenException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
@PreAuthorize("hasRole('" + AuthConstants.OAUTH_USER + "')")
class RegistrationController internal constructor(private val systemUserService: SystemUserService) {
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal jwt: Jwt): RegistrationResponse {
        val su = systemUserService.find(jwt.subject) ?: throw ForbiddenException("Authenticated, but unregistered")
        return RegistrationResponse(su)
    }

    @PostMapping(value = ["/register"])
    fun register(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody(required = false) registrationRequest: RegistrationRequest
    ): RegistrationResponse {
        return systemUserService.register(jwt)
    }
}
