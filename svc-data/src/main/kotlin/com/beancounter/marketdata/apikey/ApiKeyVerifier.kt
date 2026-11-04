package com.beancounter.marketdata.apikey

import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.ApiKey
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Authenticates a presented API key, raw or as a signed device assertion, and
 * returns the key only while it is usable. Issuing and revoking keys lives in
 * [ApiKeyService].
 */
@Service
@Transactional
class ApiKeyVerifier(
    private val apiKeyRepository: ApiKeyRepository,
    private val deviceAssertions: DeviceAssertions
) {
    /** The device key behind a valid, unspent assertion; the key must still be usable. */
    fun verifyDevice(assertion: String): ApiKey {
        val keyId = deviceAssertions.keyId(assertion)
        val found = apiKeyRepository.findById(keyId).orElseThrow { UnauthorizedException(INVALID_ASSERTION) }
        val publicKey = found.publicKey ?: throw UnauthorizedException(INVALID_ASSERTION)
        deviceAssertions.verify(assertion, keyId, publicKey)
        return usable(found)
    }

    /**
     * Hash-lookup a raw key and validate it. On success, [ApiKey.lastUsedAt]
     * is touched so stale keys are visible for audit.
     */
    fun verify(rawKey: String): ApiKey {
        val found =
            apiKeyRepository.findByKeyHash(ApiKeyGenerator.hash(rawKey))
                ?: throw UnauthorizedException(INVALID_API_KEY)
        // A device key's hash is computed from its public key, which anyone
        // may know: presented as a raw key it would match. Device keys only
        // ever authenticate by a signed assertion.
        if (found.publicKey != null) {
            throw UnauthorizedException(INVALID_API_KEY)
        }
        return usable(found)
    }

    private fun usable(found: ApiKey): ApiKey {
        val refusal = refusal(found)
        if (refusal != null) {
            throw UnauthorizedException(refusal)
        }
        // Write-per-verify is acceptable: phase 2 caches the exchanged JWT
        // for its lifetime, so this runs about once an hour per active key,
        // not per request.
        found.lastUsedAt = auditInstant()
        return apiKeyRepository.save(found)
    }

    // Why the key may not authenticate, checked in this order; null when it may.
    private fun refusal(found: ApiKey): String? {
        val parentId = found.parentId
        val expiresAt = found.expiresAt
        return when {
            found.revokedAt != null -> REVOKED
            // A widget key never outlives its device key, however the device key was revoked.
            parentId != null &&
                apiKeyRepository.findById(parentId).map { it.revokedAt == null }.orElse(false) != true -> REVOKED
            // Offboarded (deactivated) owners must not authenticate - a key
            // is never more alive than the SystemUser it belongs to.
            !found.owner.active -> "API key owner is inactive"
            expiresAt != null && expiresAt.isBefore(Instant.now()) -> "API key has expired"
            else -> null
        }
    }

    private companion object {
        const val INVALID_API_KEY = "Invalid API key"
        const val INVALID_ASSERTION = "Invalid device assertion"
        const val REVOKED = "API key has been revoked"
    }
}