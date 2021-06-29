package com.beancounter.auth.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping

@Service
@Configuration
@EnableFeignClients(basePackages = ["com.beancounter.auth"])
/**
 * OAuth2 login service to service both User and M2M authentication requests
 */
class LoginService(private val authGateway: AuthGateway, private val jwtDecoder: JwtDecoder) {
    private val log = LoggerFactory.getLogger(LoginService::class.java)

    @Value("\${spring.security.oauth2.registration.custom.client-id:bc-service}")
    private lateinit var clientId: String

    @Value("\${spring.security.oauth2.registration.custom.client-secret:not-set}")
    private lateinit var secret: String

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
            client_id = clientId
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
     * The OAuth 2 gateway call.
     */
    @FeignClient(name = "oauth", url = "\${auth.uri:http://keycloak:9620/auth}", configuration = [AuthBeans::class])
    interface AuthGateway {
        @PostMapping(
            value = ["/realms/\${auth.realm:bc-dev}/protocol/openid-connect/token"],
            consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun login(authRequest: AuthRequest): OAuth2Response
    }

    /**
     * Interface to support various oAuth login request types.
     */
    interface AuthRequest

    /**
     * OAuth2 interactive login request. These properties are interpreted literally by Spring, so
     * need the underscores in the variable names otherwise they're not mapped correctly
     *
     * Spring won't map val properties.
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
        var grant_type: String = AuthorizationGrantType.CLIENT_CREDENTIALS.value,
    ) : AuthRequest
}
