package com.beancounter.auth.client

import com.beancounter.auth.model.OAuth2Response
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping

/**
 * OAuth2 client-credential login service to service M2M authentication requests
 */
@Service
@ConditionalOnProperty(value = ["auth.enabled"], havingValue = "true", matchIfMissing = true)
class LoginService(private val authGateway: AuthGateway, private val jwtDecoder: JwtDecoder) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Value("\${spring.security.oauth2.registration.custom.client-id:bc-service}")
    private lateinit var clientId: String

    @Value("\${spring.security.oauth2.registration.custom.client-secret:not-set}")
    private lateinit var secret: String

    @Value("\${auth.audience:beancounter:service}")
    private lateinit var audience: String

    fun login(user: String, password: String, clientId: String = this.clientId) {
        val loginRequest = LoginRequest(
            client_id = clientId,
            username = user,
            password = password,
        )
        val response = authGateway.login(loginRequest)
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(
            jwtDecoder.decode(
                response.token
            )
        )
        log.info("Logged in as {}", user)
    }

    /**
     * m2m login using preconfigured secret.
     *
     * Returns token if the call is successful.
     *
     * @return token
     */
    fun login(): String? {
        if ("not-set" == secret) {
            return null
        }
        val login = MachineRequest(
            client_secret = secret,
            client_id = clientId,
            audience = audience,
        )
        val response = authGateway.login(login)
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(
            jwtDecoder.decode(
                response.token
            )
        )
        log.info("Service logged into {}", clientId)
        return response.token
    }

    /**
     * Interface to support various oAuth login request types.
     */
    interface AuthRequest

    /**
     * OAuth2 interactive login request. These properties are interpreted literally by Spring, so
     * need the underscores in the variable names otherwise they're not mapped correctly
     */
    data class LoginRequest(
        var client_id: String,
        var username: String,
        var password: String,
        var grant_type: String = AuthorizationGrantType.PASSWORD.value,
    ) : AuthRequest

    /**
     * M2M request configured from environment.
     */
    data class MachineRequest(
        var client_id: String,
        var client_secret: String = "not-set",
        var audience: String,
        var scope: String = "beancounter beancounter:system",
        var grant_type: String = AuthorizationGrantType.CLIENT_CREDENTIALS.value,
    ) : AuthRequest

    /**
     * Gateway call to the Auth server.
     */
    @FeignClient(name = "oauth", url = "\${auth.uri:http://keycloak:9620/auth/}")
    interface AuthGateway {
        @PostMapping(
            value = ["oauth/token"],
            consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun login(authRequest: AuthRequest): OAuth2Response
    }
}
