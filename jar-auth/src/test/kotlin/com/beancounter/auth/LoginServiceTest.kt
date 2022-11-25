package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.BcJson
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles

/**
 * Can the login service authenticate the user using OAuth?
 */
@SpringBootTest(classes = [ClientPasswordConfig::class])
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    FeignAutoConfiguration::class
)
@ActiveProfiles("auth")
@AutoConfigureWireMock(port = 0)
class LoginServiceTest {

    @MockBean
    private lateinit var oAuthConfig: OAuthConfig

    @MockBean
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var authGateway: LoginService.AuthGateway

    @Value("\${auth.client}")
    private lateinit var client: String

    private var objectMapper = BcJson().objectMapper

    @BeforeEach
    @Throws(Exception::class)
    fun mockJwtService() {
        `when`(jwtDecoder.decode(any())).thenReturn(Mockito.mock(Jwt::class.java))

        // Mock expired token response
        // Todo: Not properly implemented as it expects JSON body.  Need to figure out mocking multipart params.
        stubFor(
            WireMock.post("/oauth/token")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
                                    ClassPathResource("token-response.json")
                                        .file,
                                    HashMap::class.java
                                )
                            )
                        )
                        .withStatus(200)
                )
        )

        stubFor(
            WireMock.post("/.well-known/openid-configuration")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
                                    ClassPathResource("./openid-config.json")
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
            .hasNoNullFieldsOrPropertiesExcept("refreshToken")
    }
}
