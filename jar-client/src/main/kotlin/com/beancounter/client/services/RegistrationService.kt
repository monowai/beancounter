package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

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
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val jwtDecoder: JwtDecoder,
    private val tokenService: TokenService
) {
    fun login(loginRequest: LoginRequest): OpenIdResponse {
        val openIdResponse =
            restClient
                .post()
                .uri("/api/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .body(OpenIdResponse::class.java)
                ?: throw BusinessException("Failed to authenticate")

        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwtDecoder.decode(openIdResponse.token))
        log.info("Logged in ${loginRequest.user}")
        return openIdResponse
    }

    fun register(registrationRequest: RegistrationRequest): SystemUser {
        val response =
            restClient
                .post()
                .uri("/api/register")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registrationRequest)
                .retrieve()
                .body(RegistrationResponse::class.java)
                ?: throw UnauthorizedException("Your request was rejected. Have you logged in?")
        return response.data
    }

    fun me(): SystemUser {
        val response =
            restClient
                .get()
                .uri("/api/me")
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .body(RegistrationResponse::class.java)
                ?: throw BusinessException("Failed to get user info")
        return response.data
    }

    val token: String
        get() = tokenService.bearerToken

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}