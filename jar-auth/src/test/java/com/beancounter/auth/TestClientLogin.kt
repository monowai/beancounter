package com.beancounter.auth

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.client.OAuth2Response
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.common.Json.getObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [LoginService::class, LoginService.AuthGateway::class], properties = ["auth.enabled=true"])
@ImportAutoConfiguration(
    AuthClientConfig::class
)
@ActiveProfiles("auth")
@AutoConfigureWireMock(port = 0)
/**
 * Can the login service authenticate the user using OAuth?
 */
class TestClientLogin {
    @Autowired
    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var authGateway: LoginService.AuthGateway

    @Value("\${auth.client}")
    private val client: String? = null

    @BeforeEach
    @Throws(Exception::class)
    fun mockKeyCloak() {
        // Certs!
        stubFor(
            WireMock.get("/auth/realms/bc-test/protocol/openid-connect/certs")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            getObjectMapper().writeValueAsString(
                                getObjectMapper().readValue(
                                    ClassPathResource("./kc-certs.json")
                                        .file,
                                    HashMap::class.java
                                )
                            )
                        )
                        .withStatus(200)
                )
        )

        // Mock expired token response
        stubFor(
            WireMock.post("/auth/realms/bc-test/protocol/openid-connect/token")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            getObjectMapper().writeValueAsString(
                                getObjectMapper().readValue(
                                    ClassPathResource("./kc-response.json")
                                        .file,
                                    HashMap::class.java
                                )
                            )
                        )
                        .withStatus(200)
                )
        )
    }

    @Test
    fun is_ResponseSerializing() {
        val login = LoginService.AuthRequest(username = "demo", password = "test", client_id = client)

        Assertions.assertThat<OAuth2Response>(authGateway.login(login))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun is_TokenExpiredThrowing() {
        assertThrows(
            JwtValidationException::class.java
        ) { loginService.login("demo", "test", "test") }
    }
}
