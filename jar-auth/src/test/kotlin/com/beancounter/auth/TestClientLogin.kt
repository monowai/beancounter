package com.beancounter.auth

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.BcJson
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.assertj.core.api.Assertions.assertThat
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

/**
 * Can the login service authenticate the user using OAuth?
 */
@SpringBootTest(classes = [LoginService::class, LoginService.AuthGateway::class], properties = ["auth.enabled=true"])
@ImportAutoConfiguration(
    AuthClientConfig::class
)
@ActiveProfiles("auth")
@AutoConfigureWireMock(port = 0)
class TestClientLogin {
    @Autowired
    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var authGateway: LoginService.AuthGateway

    @Value("\${auth.client}")
    private lateinit var client: String

    private var objectMapper = BcJson().objectMapper

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
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
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
        // Todo: Not properly implemented as it expects JSON body.  Need to figure out mocking multipart params.
        stubFor(
            WireMock.post("/auth/realms/bc-test/protocol/openid-connect/token")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
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
    fun is_LoginGatewayWorking() {

        assertThat(
            authGateway.login(
                LoginService.LoginRequest(
                    client_id = client,
                    username = "demo",
                    password = "test"
                )
            )
        )
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun is_ExpiredMachineRequestWorking() {
        assertThrows(
            JwtValidationException::class.java
        ) { loginService.login() }
    }

    @Test
    fun is_ExpiredLoginThrowing() {
        assertThrows(
            JwtValidationException::class.java
        ) { loginService.login("demo", "test") }
    }
}
