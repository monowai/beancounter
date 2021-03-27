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
class LoginService(private val authGateway: AuthGateway, private val jwtDecoder: JwtDecoder) {
    private val log = LoggerFactory.getLogger(LoginService::class.java)

    @Value("\${spring.security.oauth2.registration.custom.client-id:bc-service}")
    private val clientId: String? = null

    @Value("\${spring.security.oauth2.registration.custom.client-secret:not-set}")
    private val secret: String? = null

    fun login(user: String?, password: String?, client: String?) {
        val authRequest = AuthRequest(username = user, password = password, client_id = client)
        val response = authGateway.login(authRequest)!!
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
        val login = AuthRequest(
            grant_type = AuthorizationGrantType.CLIENT_CREDENTIALS.value,
            client_id = clientId,
            client_secret = secret
        )
        val response = authGateway.login(login)!!
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(
            jwtDecoder.decode(
                response.token
            )
        )
        log.info("Service logged into {}", clientId)
        return response.token
    }

    @FeignClient(name = "oauth", url = "\${auth.uri:http://keycloak:9620/auth}", configuration = [AuthBeans::class])
    interface AuthGateway {
        @PostMapping(
            value = ["/realms/\${auth.realm:bc-dev}/protocol/openid-connect/token"],
            consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun login(authRequest: AuthRequest?): OAuth2Response?
    }

    data class AuthRequest(
        val username: String? = null,
        val password: String? = null,
        val client_id: String? = null,
        val client_secret: String? = null,
        val grant_type: String? = AuthorizationGrantType.PASSWORD.value,

    )
}
