package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Handles client side registration duties.
 */
@Service
@ConditionalOnProperty(
    value = ["auth.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class RegistrationService(
    private val registrationGateway: RegistrationGateway,
    private val jwtDecoder: JwtDecoder,
    private val tokenService: TokenService
) {
    fun login(loginRequest: LoginRequest): OpenIdResponse {
        val openIdResponse = registrationGateway.auth(loginRequest)
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwtDecoder.decode(openIdResponse.token))
        log.info("Logged in ${loginRequest.user}")
        return openIdResponse
    }

    fun register(registrationRequest: RegistrationRequest): SystemUser {
        val (data) =
            registrationGateway
                .register(
                    token,
                    registrationRequest
                )
                ?: throw UnauthorizedException("Your request was rejected. Have you logged in?")
        return data
    }

    fun me(): SystemUser = registrationGateway.me(token).data

    val token: String
        get() = tokenService.bearerToken

    /**
     * HTTP gateway calls to svc-data
     */
    @FeignClient(
        name = "registrationGw",
        url = "\${marketdata.url:http://localhost:9510}"
    )
    interface RegistrationGateway {
        @PostMapping(
            value = ["/api/register"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
            consumes = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun register(
            @RequestHeader("Authorization") bearerToken: String,
            registrationRequest: RegistrationRequest
        ): RegistrationResponse?

        @GetMapping(
            value = ["/api/me"],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun me(
            @RequestHeader("Authorization") bearerToken: String
        ): RegistrationResponse

        @PostMapping(
            value = ["/api/auth"],
            produces = [MediaType.APPLICATION_JSON_VALUE],
            consumes = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun auth(loginRequest: LoginRequest): OpenIdResponse
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}