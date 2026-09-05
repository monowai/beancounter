package com.beancounter.marketdata.apikey

import com.beancounter.auth.AuthConfig
import com.beancounter.common.model.ApiKey
import com.beancounter.common.model.SystemUser
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Base64

/**
 * Unit-level coverage for [BcTokenIssuer]'s PEM signing-key path (no Spring
 * context - a plain RSA key pair generated in-test, PEM-encoded exactly the
 * way an operator would configure `auth.bc-issuer.signing-key`).
 */
internal class BcTokenIssuerTest {
    private fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun pemEncode(der: ByteArray): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
    }

    private fun issuer(
        signingKeyPem: String = "",
        bcIssuerUri: String = "http://localhost:9510/api/"
    ): BcTokenIssuer =
        BcTokenIssuer(
            authConfig = AuthConfig(claimEmail = "email").apply { this.bcIssuerUri = bcIssuerUri },
            signingKeyPem = signingKeyPem,
            ttl = Duration.ofMinutes(5)
        )

    private fun apiKeyFor(
        ownerId: String,
        scopes: String = "beancounter beancounter:user"
    ): ApiKey =
        ApiKey(
            owner = SystemUser(id = ownerId, email = "$ownerId@testing.com"),
            name = "test",
            prefix = "bc_abcdefg12",
            keyHash = "hash-$ownerId",
            scopes = scopes
        )

    @Test
    fun `mint round-trips through a pem-encoded signing key`() {
        val keyPair = rsaKeyPair()
        val issuer = issuer(signingKeyPem = pemEncode(keyPair.private.encoded))
        val apiKey = apiKeyFor("owner-1")

        val jwt = issuer.mint(apiKey)

        assertThat(jwt.claims["iss"].toString()).isEqualTo("http://localhost:9510/api/")
        assertThat(jwt.claims["sub"]).isEqualTo("owner-1")
        assertThat(jwt.claims["email"]).isEqualTo("owner-1@testing.com")
        assertThat(jwt.claims["scope"]).isEqualTo("beancounter beancounter:user")
        assertThat(jwt.claims["bc:api_key_id"]).isEqualTo(apiKey.id)

        val signedJwt = SignedJWT.parse(jwt.tokenValue)
        assertThat(signedJwt.verify(RSASSAVerifier(keyPair.public as RSAPublicKey))).isTrue()
    }

    @Test
    fun `kid is stable across mints from the same signing key`() {
        val issuer = issuer(signingKeyPem = pemEncode(rsaKeyPair().private.encoded))
        val apiKey = apiKeyFor("owner-2")

        val first = issuer.mint(apiKey)
        val second = issuer.mint(apiKey)

        assertThat(first.headers["kid"]).isEqualTo(second.headers["kid"])
        assertThat(first.headers["kid"]).isNotNull()
    }

    @Test
    fun `blank signing key generates an ephemeral key and still mints valid tokens`() {
        val issuer = issuer(signingKeyPem = "")
        val apiKey = apiKeyFor("owner-3")

        val jwt = issuer.mint(apiKey)

        assertThat(jwt.tokenValue).isNotBlank()
        assertThat(issuer.publicJwks()["keys"]).isNotNull()
    }
}