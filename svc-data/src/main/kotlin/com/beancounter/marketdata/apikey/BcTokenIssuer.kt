package com.beancounter.marketdata.apikey

import com.beancounter.auth.AuthConfig
import com.beancounter.common.model.ApiKey
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Mints short-lived JWTs in exchange for a verified BC API key (phase 2 -
 * bc-claude/MCP.md): the rest of the stack already trusts Auth0-issued
 * JWTs, so a token minted here - with `iss` = [AuthConfig.bcIssuerUri] and
 * the key's owner/scopes as claims - is accepted by every other service
 * once `auth.bc-issuer.uri` is configured there too (see jar-auth's
 * OAuthConfig issuer-routing decoder).
 *
 * Signing key: PEM-encoded PKCS#8 RSA private key from
 * `auth.bc-issuer.signing-key` when set; otherwise an ephemeral RSA-2048
 * key is generated at startup (logged as a WARN - minted tokens won't
 * survive a restart, since JWKS lookups against the old kid would fail).
 */
@Service
class BcTokenIssuer(
    private val authConfig: AuthConfig,
    @Value($$"${auth.bc-issuer.signing-key:}") private val signingKeyPem: String,
    @Value($$"${auth.bc-issuer.ttl:PT1H}") private val ttl: Duration = Duration.ofHours(1)
) {
    init {
        // Blank would mint tokens with iss="" that every relying service
        // (including svc-data itself) rejects - fail startup instead.
        check(authConfig.bcIssuerUri.isNotBlank() && authConfig.bcIssuerUri.endsWith("/")) {
            "auth.bc-issuer.uri must be configured and end with '/' (got: '${authConfig.bcIssuerUri}')"
        }
    }

    private val rsaKey: RSAKey = loadOrGenerateKey(signingKeyPem)
    private val encoder = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))

    /** Seconds until an issued token expires - echoed back as `expires_in`. */
    val ttlSeconds: Long get() = ttl.seconds

    /** The public half of the signing key, safe to publish at `/.well-known/jwks.json`. */
    fun publicJwks(): Map<String, Any> = JWKSet(rsaKey.toPublicJWK()).toJSONObject(true)

    fun mint(apiKey: ApiKey): Jwt {
        val now = Instant.now()
        val header =
            JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId(rsaKey.keyID)
                .build()
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(authConfig.bcIssuerUri)
                .subject(apiKey.owner.id)
                .audience(listOf(authConfig.audience))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", apiKey.owner.email)
                .claim("scope", apiKey.scopes)
                .claim("bc:api_key_id", apiKey.id)
                .build()
        return encoder.encode(JwtEncoderParameters.from(header, claims))
    }

    companion object {
        private val log = LoggerFactory.getLogger(BcTokenIssuer::class.java)

        private fun loadOrGenerateKey(pem: String): RSAKey =
            if (pem.isBlank()) {
                log.warn(
                    "auth.bc-issuer.signing-key is not set - generating an ephemeral RSA " +
                        "key. Minted tokens will not be verifiable after a restart."
                )
                generateEphemeralKey()
            } else {
                fromPem(pem)
            }

        private fun generateEphemeralKey(): RSAKey =
            try {
                RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint(true)
                    .generate()
            } catch (e: JOSEException) {
                throw IllegalStateException("Failed to generate an ephemeral RSA signing key", e)
            }

        private fun fromPem(pem: String): RSAKey {
            val der = Base64.getDecoder().decode(pkcs8Body(pem))
            val privateKey =
                KeyFactory
                    .getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateCrtKey
            val publicKey =
                KeyFactory
                    .getInstance("RSA")
                    .generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey
            return try {
                RSAKey
                    .Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint()
                    .build()
            } catch (e: JOSEException) {
                throw IllegalStateException("Failed to compute a thumbprint for the configured signing key", e)
            }
        }

        private fun pkcs8Body(pem: String): String =
            pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
    }
}