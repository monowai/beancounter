package com.beancounter.client.services

import com.beancounter.auth.common.TokenService
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Handles client side registration duties.
 */
@Service
class RegistrationService(private val registrationGateway: RegistrationGateway, private val tokenService: TokenService) {
    fun register(registrationRequest: RegistrationRequest): SystemUser {
        val (data) = registrationGateway
            .register(tokenService.bearerToken, registrationRequest)
            ?: throw UnauthorizedException("Your request was rejected. Have you logged in?")
        return data
    }

    fun me(): SystemUser {
        val (data) = registrationGateway.me(tokenService.bearerToken)
            ?: throw UnauthorizedException("User account is not registered")
        return data
    }

    val token: String?
        get() = tokenService.token

    val jwtToken: JwtAuthenticationToken?
        get() = tokenService.jwtToken

    @FeignClient(name = "registrationGw", url = "\${marketdata.url:http://localhost:9510/api}")
    interface RegistrationGateway {
        @PostMapping(
            value = ["/register"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
            consumes = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun register(
            @RequestHeader("Authorization") bearerToken: String?,
            registrationRequest: RegistrationRequest?
        ): RegistrationResponse?

        @GetMapping(
            value = ["/me"],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun me(@RequestHeader("Authorization") bearerToken: String?): RegistrationResponse?
    }
}
