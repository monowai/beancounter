package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestTemplate
import java.util.Date

/**
 * Covers [OAuthConfig.jwtDecoder] when a second trusted issuer
 * (`auth.bc-issuer.uri`) is configured alongside Auth0: tokens are routed to
 * the matching per-issuer decoder by their (unverified) `iss` claim, and
 * still validated (signature + standard claims) by that decoder.
 */
@SpringBootTest(classes = [ClientPasswordConfig::class, RestTemplate::class])
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    OAuthConfig::class
)
@ActiveProfiles("dualissuer")
class OAuthConfigDualIssuerTest {
    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(
                    com.github.tomakehurst.wiremock.core.WireMockConfiguration
                        .options()
                        .dynamicPort()
                ).configureStaticDsl(true)
                .build()

        @JvmStatic
        @DynamicPropertySource
        fun wireMockProps(registry: DynamicPropertyRegistry) {
            registry.add("wiremock.server.port") { wireMock.port }
        }

        private val auth0Key: RSAKey =
            RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("auth0-kid")
                .generate()

        private val bcKey: RSAKey =
            RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("bc-kid")
                .generate()
    }

    @Autowired
    private lateinit var authConfig: AuthConfig

    @BeforeEach
    fun stubJwks() {
        stubJwksAt("/auth0/.well-known/jwks.json", auth0Key)
        stubJwksAt("/bc/.well-known/jwks.json", bcKey)
    }

    private fun stubJwksAt(
        path: String,
        key: RSAKey
    ) {
        WireMock.stubFor(
            WireMock
                .get(path)
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(JWKSet(key.toPublicJWK()).toString())
                        .withStatus(200)
                )
        )
    }

    private fun signedToken(
        key: RSAKey,
        issuer: String,
        audience: String = "audience-api-url",
        subject: String = "user-1"
    ): String {
        val now = Date()
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(now)
                .expirationTime(Date(now.time + 60_000))
                .build()
        val signedJwt =
            SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
                claims
            )
        signedJwt.sign(RSASSASigner(key))
        return signedJwt.serialize()
    }

    @Test
    fun `decodes token issued by the bc-issuer when both issuers configured`() {
        val decoder = OAuthConfig().jwtDecoder(authConfig)
        val token = signedToken(bcKey, authConfig.bcIssuerUri)

        val jwt = decoder.decode(token)

        assertThat(jwt.issuer.toString()).isEqualTo(authConfig.bcIssuerUri)
    }

    @Test
    fun `still decodes an auth0-issued token when both issuers configured`() {
        val decoder = OAuthConfig().jwtDecoder(authConfig)
        val token = signedToken(auth0Key, authConfig.issuer)

        val jwt = decoder.decode(token)

        assertThat(jwt.issuer.toString()).isEqualTo(authConfig.issuer)
    }

    @Test
    fun `rejects a token from an untrusted issuer`() {
        val decoder = OAuthConfig().jwtDecoder(authConfig)
        val token = signedToken(bcKey, "https://evil.example/")

        assertThatThrownBy { decoder.decode(token) }
            .isInstanceOf(BadJwtException::class.java)
    }

    @Test
    fun `rejects a garbage non-JWT string`() {
        val decoder = OAuthConfig().jwtDecoder(authConfig)

        assertThatThrownBy { decoder.decode("not-a-jwt") }
            .isInstanceOf(BadJwtException::class.java)
    }

    @Test
    fun `rejects an encrypted JWE token instead of leaking a runtime exception`() {
        val decoder = OAuthConfig().jwtDecoder(authConfig)
        val jwe =
            EncryptedJWT(
                JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A128GCM).build(),
                JWTClaimsSet.Builder().issuer(authConfig.bcIssuerUri).build()
            )
        jwe.encrypt(RSAEncrypter(bcKey.toRSAPublicKey()))

        assertThatThrownBy { decoder.decode(jwe.serialize()) }
            .isInstanceOf(BadJwtException::class.java)
    }

    @Test
    fun `fails fast when the bc issuer uri lacks the trailing slash`() {
        val original = authConfig.bcIssuerUri
        try {
            authConfig.bcIssuerUri = original.trimEnd('/')

            assertThatThrownBy { OAuthConfig().jwtDecoder(authConfig) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("must end with '/'")
        } finally {
            authConfig.bcIssuerUri = original
        }
    }

    @Test
    fun `fails fast when both issuers are configured identically`() {
        val original = authConfig.bcIssuerUri
        try {
            authConfig.bcIssuerUri = authConfig.issuer

            assertThatThrownBy { OAuthConfig().jwtDecoder(authConfig) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("must differ")
        } finally {
            authConfig.bcIssuerUri = original
        }
    }
}