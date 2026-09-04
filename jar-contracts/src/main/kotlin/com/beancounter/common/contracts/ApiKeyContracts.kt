package com.beancounter.common.contracts

import java.time.Instant

/**
 * Request/response contracts for BC-issued API keys (MCP/agent access,
 * phase 1 - bc-claude/MCP.md). [ApiKeyView] never carries the hash or the
 * raw key; [ApiKeyCreatedResponse] is the only place the raw key appears,
 * returned exactly once, at creation.
 */
data class ApiKeyRequest(
    val name: String,
    // Empty means "apply the service defaults" - the allowed/default scope
    // sets are owned by ApiKeyService (svc-data), the single source of truth.
    val scopes: Collection<String> = emptyList(),
    val expiresAt: Instant? = null
)

data class ApiKeyView(
    val id: String,
    val name: String,
    val prefix: String,
    val scopes: Collection<String>,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?
)

data class ApiKeyCreatedResponse(
    override val data: ApiKeyView,
    val apiKey: String
) : Payload<ApiKeyView>

data class ApiKeysResponse(
    override val data: Collection<ApiKeyView>
) : Payload<Collection<ApiKeyView>>

data class ApiKeyResponse(
    override val data: ApiKeyView
) : Payload<ApiKeyView>