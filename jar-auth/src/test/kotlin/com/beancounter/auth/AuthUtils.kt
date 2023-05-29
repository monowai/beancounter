package com.beancounter.auth

import com.beancounter.common.utils.BcJson
import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

/**
 * Mock out OpenID configuration via Wiremock.
 *
 * Given the random port of wiremock, we are unable to template the URL to lookup various
 * well known endpoints.  Instead we will rewrite the template with the address that
 * WireMock is listening on.
 *
 * You can't autowire JwtDecoder using this approach as beans are initialised before
 * wiremock is stood up
 */
class AuthUtils {
    companion object {
        @JvmStatic
        fun mockOpenConnect(authConfig: AuthConfig) {
            // Mock expired token response
            // Todo: Not properly implemented as it expects JSON body.  Need to figure out mocking multipart params.
            WireMock.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(
                                BcJson().objectMapper.writeValueAsString(
                                    BcJson().objectMapper.readValue(
                                        ClassPathResource("token-response.json")
                                            .file,
                                        HashMap::class.java,
                                    ),
                                ),
                            )
                            .withStatus(200),
                    ),
            )

            WireMock.stubFor(
                WireMock.get("/.well-known/openid-configuration")
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(
                                BcJson().objectMapper.writeValueAsString(
                                    remapLocalhostForWiremock(authConfig),
                                ),
                            )
                            .withStatus(200),
                    ),
            )
            WireMock.stubFor(
                WireMock.get("/.well-known/jwks.json")
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(
                                BcJson().objectMapper.writeValueAsString(
                                    BcJson().objectMapper.readValue(
                                        ClassPathResource("./auth0-jwks.json")
                                            .file,
                                        HashMap::class.java,
                                    ),
                                ),
                            )
                            .withStatus(200),
                    ),
            )
        }

        @JvmStatic
        private fun remapLocalhostForWiremock(authConfig: AuthConfig, file: String = "./openid-config.json"): Map<String, Any> {
            // This is to support mocking via WireMock.
            val localTemplate = "{localhost}"
            val configuration = BcJson().objectMapper.readValue(
                ClassPathResource(file)
                    .file,
                HashMap::class.java,
            )
            val results: MutableMap<String, String> = mutableMapOf()
            configuration.forEach { entry ->
                results[entry.key.toString()] =
                    if (entry.value.toString().startsWith(localTemplate)) {
                        entry.value.toString().replace(localTemplate, authConfig.issuer)
                    } else {
                        entry.value.toString()
                    }
            }
            return results
        }
    }
}
