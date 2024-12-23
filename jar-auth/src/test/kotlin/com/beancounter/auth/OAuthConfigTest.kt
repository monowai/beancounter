package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestTemplate

/**
 * Verify code in OauthConfig which is generally mocked.
 */
@SpringBootTest(classes = [ClientPasswordConfig::class, RestTemplate::class])
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    FeignAutoConfiguration::class,
    OAuthConfig::class
)
@ActiveProfiles("auth")
@AutoConfigureWireMock(port = 0)
class OAuthConfigTest {
    @Autowired
    private lateinit var authConfig: AuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun mockJwtService() {
        WireMock.stubFor(
            WireMock
                .post("/oauth/token")
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                        ).withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
                                    ClassPathResource("user-token-response.json")
                                        .file,
                                    HashMap::class.java
                                )
                            )
                        ).withStatus(200)
                )
        )

        WireMock.stubFor(
            WireMock
                .get("/.well-known/openid-configuration")
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                        ).withBody(
                            objectMapper.writeValueAsString(
                                remapLocalhostForWiremock(authConfig)
                            )
                        ).withStatus(200)
                )
        )
        WireMock.stubFor(
            WireMock
                .get("/.well-known/jwks.json")
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                        ).withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
                                    ClassPathResource("./auth0-jwks.json")
                                        .file,
                                    HashMap::class.java
                                )
                            )
                        ).withStatus(200)
                )
        )
    }

    private fun remapLocalhostForWiremock(
        authConfig: AuthConfig,
        file: String = "./openid-config.json"
    ): Map<String, Any> {
        // This is to support mocking via WireMock.
        val localTemplate = "{localhost}"
        val configuration =
            objectMapper.readValue(
                ClassPathResource(file)
                    .file,
                HashMap::class.java
            )
        val results: MutableMap<String, String> = mutableMapOf()
        configuration.forEach { entry ->
            results[entry.key.toString()] =
                if (entry.value.toString().startsWith(localTemplate)) {
                    entry.value.toString().replace(
                        localTemplate,
                        authConfig.issuer
                    )
                } else {
                    entry.value.toString()
                }
        }
        return results
    }

    @Test
    fun authConfig() {
        val decoder = OAuthConfig().jwtDecoder(authConfig)
        assertThat(decoder).isNotNull
    }
}