package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.ApiKeyCreatedResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeyView
import com.beancounter.common.contracts.ApiKeysResponse
import com.beancounter.common.contracts.DeviceKeyRequest
import com.beancounter.common.contracts.WidgetKeyRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.ApiKey
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Business logic for BC-issued API keys (MCP/agent access, phase 1 of
 * bc-claude/MCP.md). Only a hash of the key is ever persisted; the raw key
 * is returned exactly once, by [create]. Presenting a key to authenticate
 * lives in [ApiKeyVerifier]; which scopes a key may carry, in [ApiKeyScopes].
 */
@Service
@Transactional
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyGenerator: ApiKeyGenerator,
    private val systemUserService: SystemUserService,
    private val apiKeyScopes: ApiKeyScopes
) {
    fun create(request: ApiKeyRequest): ApiKeyCreatedResponse {
        val owner = systemUserService.getOrThrow()
        if (request.name.isBlank()) {
            throw BusinessException("API key name is required")
        }
        val expiresAt = request.expiresAt
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw BusinessException("expiresAt must be in the future")
        }
        val scopes = apiKeyScopes.validated(request.scopes)
        val generated = apiKeyGenerator.generate()

        val saved =
            apiKeyRepository.save(
                ApiKey(
                    owner = owner,
                    name = request.name,
                    prefix = generated.prefix,
                    keyHash = generated.keyHash,
                    scopes = scopes.joinToString(" "),
                    expiresAt = request.expiresAt
                )
            )
        return ApiKeyCreatedResponse(
            data = saved.toView(),
            apiKey = generated.apiKey
        )
    }

    fun list(): ApiKeysResponse {
        val owner = systemUserService.getOrThrow()
        return ApiKeysResponse(
            apiKeyRepository.findByOwnerId(owner.id).map { it.toView() }
        )
    }

    /** Revokes the key and its active children: a widget key never outlives its device key. */
    fun revoke(id: String): ApiKeyResponse {
        val owner = systemUserService.getOrThrow()
        val existing = findOwnedOrThrow(id, owner.id)
        val revokedAt = auditInstant()
        if (existing.revokedAt == null) {
            existing.revokedAt = revokedAt
            apiKeyRepository.save(existing)
        }
        revokeChildren(existing.id, revokedAt)
        return ApiKeyResponse(existing.toView())
    }

    private fun revokeChildren(
        parentId: String,
        revokedAt: Instant
    ) {
        apiKeyRepository.findByParentIdAndRevokedAtIsNull(parentId).forEach { child ->
            child.revokedAt = revokedAt
            apiKeyRepository.save(child)
        }
    }

    /**
     * Registers a device-bound key for the caller: only its public half is
     * stored, and [ApiKey.keyHash] is a hash of that public key, so no raw
     * secret can ever match it.
     */
    fun createDevice(request: DeviceKeyRequest): ApiKeyResponse {
        val owner = systemUserService.getOrThrow()
        if (request.name.isBlank()) {
            throw BusinessException("Device key name is required")
        }
        return ApiKeyResponse(
            saveDeviceBound(owner, request.name, request.publicKey, apiKeyScopes.validated(emptyList())).toView()
        )
    }

    /**
     * Registers a home-screen widget's key under one of the caller's active device keys.
     * The widget's key works without biometrics, so it carries only
     * [AuthConstants.WIDGET], never a scope from the caller's token. A device holds one
     * widget key: registering another revokes the previous one.
     */
    fun createWidget(request: WidgetKeyRequest): ApiKeyResponse {
        val owner = systemUserService.getOrThrow()
        if (request.name.isBlank()) {
            throw BusinessException("Widget key name is required")
        }
        val parent = findOwnedOrThrow(request.deviceKeyId, owner.id)
        if (parent.publicKey == null || parent.parentId != null || parent.revokedAt != null) {
            throw BusinessException("A widget key must be registered under an active device key")
        }
        revokeChildren(parent.id, auditInstant())
        return ApiKeyResponse(
            saveDeviceBound(owner, request.name, request.publicKey, listOf(AuthConstants.WIDGET), parent.id).toView()
        )
    }

    // Stores only the public half; the hash and prefix scheme let the assertion
    // exchange treat device and widget keys alike.
    private fun saveDeviceBound(
        owner: SystemUser,
        name: String,
        encodedPublicKey: String,
        scopes: Collection<String>,
        parentId: String? = null
    ): ApiKey {
        val publicKey = Base64.getEncoder().encodeToString(DeviceAssertions.p256(encodedPublicKey).encoded)
        val keyHash = ApiKeyGenerator.hash("$DEVICE_HASH_DOMAIN$publicKey")
        if (apiKeyRepository.findByKeyHash(keyHash) != null) {
            throw BusinessException("This key is already registered")
        }
        return apiKeyRepository.save(
            ApiKey(
                owner = owner,
                name = name,
                prefix = "$DEVICE_PREFIX${keyHash.take(DEVICE_PREFIX_HASH_LENGTH)}",
                keyHash = keyHash,
                scopes = scopes.joinToString(" "),
                publicKey = publicKey,
                parentId = parentId
            )
        )
    }

    private fun findOwnedOrThrow(
        id: String,
        ownerId: String
    ): ApiKey {
        val found =
            apiKeyRepository.findById(id).orElseThrow {
                NotFoundException(API_KEY_NOT_FOUND)
            }
        if (found.owner.id != ownerId) {
            // Don't distinguish "not found" from "not yours" - avoids
            // leaking whether an id exists to a caller who doesn't own it.
            throw NotFoundException(API_KEY_NOT_FOUND)
        }
        return found
    }

    private fun ApiKey.toView() =
        ApiKeyView(
            id = id,
            name = name,
            prefix = prefix,
            scopes = scopes.split(" ").filter { it.isNotBlank() },
            createdAt = createdAt,
            expiresAt = expiresAt,
            lastUsedAt = lastUsedAt,
            revokedAt = revokedAt
        )

    companion object {
        private const val API_KEY_NOT_FOUND = "API key not found"
        private const val DEVICE_PREFIX = "bcd_"
        private const val DEVICE_PREFIX_HASH_LENGTH = 7

        // Keeps a device key's hash apart from any generated key's hash. It is
        // not secret, so [ApiKeyVerifier.verify] must still refuse rows with a public key.
        private const val DEVICE_HASH_DOMAIN = "device-spki:"
    }
}

// Millisecond precision so a value read back from the database compares
// equal to the value we set - JDK nanos don't survive the TIMESTAMP
// round-trip on all platforms.
internal fun auditInstant(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)