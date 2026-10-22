package com.beancounter.agent.quota

import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The weekly quota on AI turns for callers without `beancounter:ai`. A turn
 * counts once answered and stops counting [WINDOW] later, so the quota frees
 * one turn at a time rather than resetting on a calendar day. The limit is
 * `beancounter.agent.quota.weekly`, unless an administrator set one for the
 * user. Admission reads the count before the model is asked and the turn is
 * counted only once answered, so turns a user starts in parallel can run past
 * the limit by the number in flight.
 */
@Service
class AgentQuota(
    private val usage: AgentUsageRepository,
    private val limits: AgentQuotaLimitRepository,
    private val clock: Clock,
    @Value($$"${beancounter.agent.quota.weekly:10}") private val weekly: Int,
    @Value($$"${beancounter.agent.quota.contact:askmonowai@duck.com}") private val contact: String
) : TurnQuota {
    companion object {
        val WINDOW: Duration = Duration.ofDays(7)
    }

    @Transactional(readOnly = true)
    override fun allows(ownerId: String): Boolean = statusOf(ownerId).remaining > 0

    @Transactional
    override fun spend(ownerId: String) {
        val now = clock.instant()
        usage.deleteOfOwnerUpTo(ownerId, now.minus(WINDOW))
        usage.save(AgentUsage(ownerId = ownerId, usedAt = now))
    }

    /** [ownerId]'s quota now: [QuotaStatus.resetAt] is when the oldest counted turn frees up. */
    @Transactional(readOnly = true)
    fun statusOf(ownerId: String): QuotaStatus {
        val counted = usage.findByOwnerIdAndUsedAtAfterOrderByUsedAt(ownerId, clock.instant().minus(WINDOW))
        val override = limits.findById(ownerId).orElse(null)
        val limit = override?.weeklyLimit ?: weekly
        return QuotaStatus(
            limit = limit,
            used = counted.size,
            remaining = (limit - counted.size).coerceAtLeast(0),
            resetAt = counted.firstOrNull()?.usedAt?.plus(WINDOW),
            contact = contact,
            override = override != null
        )
    }

    /** Give [ownerId] a weekly limit of their own. */
    @Transactional
    fun setLimit(
        ownerId: String,
        weeklyLimit: Int
    ): QuotaStatus {
        if (weeklyLimit < 0) throw BusinessException("weeklyLimit must not be negative")
        val row = limits.findById(ownerId).orElse(null)
        if (row == null) {
            limits.save(AgentQuotaLimit(ownerId, weeklyLimit, clock.instant()))
        } else {
            row.weeklyLimit = weeklyLimit
            row.updatedAt = clock.instant()
        }
        return statusOf(ownerId)
    }

    /** Return [ownerId] to the configured default. */
    @Transactional
    fun clearLimit(ownerId: String): QuotaStatus {
        limits.deleteById(ownerId)
        return statusOf(ownerId)
    }

    /** Drop every turn counted against [ownerId]. */
    @Transactional
    fun forget(ownerId: String) {
        usage.deleteOfOwner(ownerId)
    }
}

/**
 * One user's weekly AI quota. [unlimited] callers (`beancounter:ai`) are never
 * counted, and the other fields are then zero. [contact] is who to ask for a
 * higher limit; [override] is true when an administrator set [limit].
 */
data class QuotaStatus(
    val limit: Int,
    val used: Int,
    val remaining: Int,
    val resetAt: Instant?,
    val contact: String,
    val override: Boolean = false,
    val unlimited: Boolean = false
)