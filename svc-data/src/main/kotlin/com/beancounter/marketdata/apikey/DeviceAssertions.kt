package com.beancounter.marketdata.apikey

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.UnauthorizedException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.text.ParseException
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks the ES256 assertions device-bound keys sign to buy a BC JWT
 * (RFC 7523 style): `kid`, `iss` and `sub` name the device key, `aud` is
 * [AUDIENCE], it expires within [MAX_LIFETIME], and its `jti` is accepted
 * once. Used `jti`s are remembered in memory until their assertion expires,
 * which is sound while bc-data runs as a single replica.
 */
@Component
class DeviceAssertions {
    private val used = ConcurrentHashMap<String, Instant>()

    /** The device key id an assertion claims to be from, before any check. */
    fun keyId(assertion: String): String = parse(assertion).header.keyID ?: throw invalid()

    /** Verifies [assertion] against [publicKey] and spends its `jti`. */
    fun verify(
        assertion: String,
        keyId: String,
        publicKey: String
    ) {
        val jwt = parse(assertion)
        if (jwt.header.algorithm != JWSAlgorithm.ES256) throw invalid()
        val signed =
            try {
                jwt.verify(ECDSAVerifier(p256(publicKey)))
            } catch (_: com.nimbusds.jose.JOSEException) {
                false
            }
        if (!signed) throw invalid()
        val claims = jwt.jwtClaimsSet
        val now = Instant.now()
        val expiresAt = claims.expirationTime?.toInstant() ?: throw invalid()
        val issuedAt = claims.issueTime?.toInstant() ?: throw invalid()
        val jti = claims.jwtid ?: throw invalid()
        val fresh =
            claims.issuer == keyId &&
                claims.subject == keyId &&
                AUDIENCE in claims.audience &&
                expiresAt.isAfter(now) &&
                !expiresAt.isAfter(now.plus(MAX_LIFETIME).plus(CLOCK_SKEW)) &&
                !issuedAt.isAfter(now.plus(CLOCK_SKEW))
        if (!fresh || !firstUse(jti, expiresAt, now)) throw invalid()
    }

    private fun firstUse(
        jti: String,
        expiresAt: Instant,
        now: Instant
    ): Boolean {
        used.entries.removeIf { it.value.isBefore(now) }
        return used.putIfAbsent(jti, expiresAt) == null
    }

    private fun parse(assertion: String): SignedJWT =
        try {
            SignedJWT.parse(assertion)
        } catch (_: ParseException) {
            throw invalid()
        }

    private fun invalid() = UnauthorizedException("Invalid device assertion")

    companion object {
        const val AUDIENCE = "urn:beancounter:device-token"
        private val MAX_LIFETIME: Duration = Duration.ofMinutes(5)
        private val CLOCK_SKEW: Duration = Duration.ofSeconds(30)

        /** Decodes a base64 SPKI and insists on a P-256 key. */
        fun p256(spki: String): ECPublicKey {
            val key =
                try {
                    KeyFactory
                        .getInstance("EC")
                        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(spki))) as ECPublicKey
                } catch (_: GeneralSecurityException) {
                    throw BusinessException("Device key must be a P-256 public key")
                } catch (_: IllegalArgumentException) {
                    throw BusinessException("Device key must be a P-256 public key")
                }
            if (Curve.forECParameterSpec(key.params) != Curve.P_256) {
                throw BusinessException("Device key must be a P-256 public key")
            }
            return key
        }
    }
}