package com.beancounter.auth

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.client.OAuth2Response
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.Json.getObjectMapper
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.test.context.ActiveProfiles
import java.util.HashMap

@SpringBootTest(classes = [LoginService::class, LoginService.AuthGateway::class], properties = ["auth.enabled=true"])
@ImportAutoConfiguration(
    AuthClientConfig::class
)
@ActiveProfiles("auth")
class TestClientLogin {
    @Autowired
    private val loginService: LoginService? = null

    @Autowired
    private val authGateway: LoginService.AuthGateway? = null

    @Value("\${auth.client}")
    private val client: String? = null
    @BeforeEach
    @Throws(Exception::class)
    fun mockKeyCloak() {
        if (mockInternet == null) {
            mockInternet = WireMockRule(WireMockConfiguration.options().port(9999))
            mockInternet!!.start()
        }

        // Certs!
        mockInternet!!
            .stubFor(
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
        mockInternet!!
            .stubFor(
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
        val login = LoginService.Login.builder()
            .username("demo")
            .password("test")
            .client_id(client)
            .build()
        Assertions.assertThat<OAuth2Response>(authGateway?.login(login))
            .isNotNull()
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun is_TokenExpiredThrowing() {
        org.junit.jupiter.api.Assertions.assertThrows<JwtValidationException>(
            JwtValidationException::class.java
        ) { loginService!!.login("demo", "test", "test") }
    }

    companion object {
        private var mockInternet: WireMockRule? = null
    }
}
