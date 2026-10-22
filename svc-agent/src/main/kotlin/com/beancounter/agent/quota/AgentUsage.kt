package com.beancounter.agent.quota

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** One answered AI turn by [ownerId], a `SystemUser.id`, counted against the weekly quota. */
@Entity
@Table(name = "agent_usage")
class AgentUsage(
    @Id
    val id: String = KeyGenUtils().id,
    @Column(nullable = false, length = 36)
    val ownerId: String,
    @Column(nullable = false)
    val usedAt: Instant
)

/**
 * An administrator's allowance for [ownerId], replacing the configured default:
 * [weeklyLimit] turns a week (0 disables AI), or no limit when [unlimited].
 */
@Entity
@Table(name = "agent_quota_limit")
class AgentQuotaLimit(
    @Id
    val ownerId: String,
    @Column(nullable = false)
    var weeklyLimit: Int,
    @Column(nullable = false)
    var updatedAt: Instant,
    @Column(nullable = false)
    var unlimited: Boolean = false
)