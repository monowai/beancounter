package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.ApiKeyCreatedResponse
import com.beancounter.common.contracts.ApiKeyRequest
import com.beancounter.common.contracts.ApiKeyResponse
import com.beancounter.common.contracts.ApiKeyView
import com.beancounter.common.contracts.ApiKeysResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.ApiKey
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Business logic for BC-issued API keys (MCP/agent access, phase 1 of
 * bc-claude/MCP.md). Only a hash of the key is ever persisted; the raw key
 * is returned exactly once, by [create]. [verify] is service-level only -
 * no controller exposes it yet, since token exchange (turning a verified
 * key into a JWT the rest of the stack accepts) is phase 2.
 */
@Service
@Transactional
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyGenerator: ApiKeyGenerator,
    private val systemUserService: SystemUserService
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
        val scopes = validatedScopes(request.scopes)
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

    fun revoke(id: String): ApiKeyResponse {
        val owner = systemUserService.getOrThrow()
        val existing = findOwnedOrThrow(id, owner.id)
        if (existing.revokedAt == null) {
            existing.revokedAt = auditInstant()
            apiKeyRepository.save(existing)
        }
        return ApiKeyResponse(existing.toView())
    }

    /**
     * Hash-lookup a raw key and validate it. Service-level only - phase 2's
     * token-exchange endpoint will be the first caller. On success,
     * [ApiKey.lastUsedAt] is touched so stale keys are visible for audit.
     */
    fun verify(rawKey: String): ApiKey {
        val found =
            apiKeyRepository.findByKeyHash(ApiKeyGenerator.hash(rawKey))
                ?: throw UnauthorizedException("Invalid API key")
        if (found.revokedAt != null) {
            throw UnauthorizedException("API key has been revoked")
        }
        if (!found.owner.active) {
            // Offboarded (deactivated) owners must not authenticate - a key
            // is never more alive than the SystemUser it belongs to.
            throw UnauthorizedException("API key owner is inactive")
        }
        val expiresAt = found.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw UnauthorizedException("API key has expired")
        }
        // Write-per-verify is acceptable: phase 2 caches the exchanged JWT
        // for its lifetime, so this runs about once an hour per active key,
        // not per request.
        found.lastUsedAt = auditInstant()
        return apiKeyRepository.save(found)
    }

    // Millisecond precision so a value read back from the database compares
    // equal to the value we set - JDK nanos don't survive the TIMESTAMP
    // round-trip on all platforms.
    private fun auditInstant(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

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

    private fun validatedScopes(requested: Collection<String>): Collection<String> {
        val scopes = requested.ifEmpty { DEFAULT_SCOPES }
        val invalid = scopes.filterNot { ALLOWED_SCOPES.contains(it) }
        if (invalid.isNotEmpty()) {
            throw BusinessException(
                "Scope(s) not permitted for API keys: ${invalid.joinToString(", ")}"
            )
        }
        return scopes
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

        // Scopes issuable via API key are capped at the owner's own
        // day-to-day authorities. beancounter:admin and beancounter:system
        // are never issuable this way.
        private val DEFAULT_SCOPES =
            listOf(
                AuthConstants.APP_NAME,
                AuthConstants.USER,
                AuthConstants.AI
            )
        private val ALLOWED_SCOPES = DEFAULT_SCOPES.toSet()
    }
}