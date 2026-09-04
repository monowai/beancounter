package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A BC-issued API key for programmatic/agent access (MCP tools, phase 1 of
 * bc-claude/MCP.md). The raw key is shown exactly once, at creation - only
 * its SHA-256 hash is persisted in [keyHash]. [prefix] is a display-only
 * fragment of the raw key (safe to show in listings).
 *
 * Phase 1 covers the entity, storage and management endpoints only. Turning
 * a verified key into a JWT the rest of the stack accepts is phase 2 - see
 * ApiKeyService.verify, which is service-level only for now (no endpoint).
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = "uk_api_key_hash", columnNames = ["key_hash"])
    ]
)
data class ApiKey(
    @Id val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    val owner: SystemUser,
    val name: String,
    val prefix: String,
    val keyHash: String,
    val scopes: String,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    var lastUsedAt: Instant? = null,
    var revokedAt: Instant? = null
)