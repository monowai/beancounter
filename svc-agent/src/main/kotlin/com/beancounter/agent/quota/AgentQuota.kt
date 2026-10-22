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
    override fun allows(ownerId: String): Boolean = statusOf(ownerId).let { it.unlimited || it.remaining > 0 }

    @Transactional(readOnly = true)
    override fun disabled(ownerId: String): Boolean =
        limits.findById(ownerId).map { !it.unlimited && it.weeklyLimit == 0 }.orElse(false)

    @Transactional
    override fun spend(ownerId: String) {
        val now = clock.instant()
        usage.deleteOfOwnerUpTo(ownerId, now.minus(WINDOW))
        usage.save(AgentUsage(ownerId = ownerId, usedAt = now))
    }

    /**
     * [ownerId]'s quota now: [QuotaStatus.resetAt] is when the oldest counted
     * turn frees up. An administrator's unlimited grant reports [QuotaStatus.unlimited]
     * with the turns still counted in [QuotaStatus.used].
     */
    @Transactional(readOnly = true)
    fun statusOf(ownerId: String): QuotaStatus {
        val counted = usage.findByOwnerIdAndUsedAtAfterOrderByUsedAt(ownerId, clock.instant().minus(WINDOW))
        val override = limits.findById(ownerId).orElse(null)
        if (override?.unlimited == true) {
            return unlimited().copy(used = counted.size, override = true)
        }
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

    /** The default limit, and the status of every user with a turn this week or an override. */
    @Transactional(readOnly = true)
    fun overview(): QuotaOverview {
        val owners = usage.ownersActiveAfter(clock.instant().minus(WINDOW)) + limits.findAll().map { it.ownerId }
        return QuotaOverview(weekly, owners.distinct().associateWith(::statusOf))
    }

    /** The status of a caller with no quota (`beancounter:ai`): nothing counted, the same contact. */
    fun unlimited(): QuotaStatus =
        QuotaStatus(limit = 0, used = 0, remaining = 0, resetAt = null, contact = contact, unlimited = true)

    /** Give [ownerId] a weekly limit of their own. */
    @Transactional
    fun setLimit(
        ownerId: String,
        weeklyLimit: Int
    ): QuotaStatus {
        if (weeklyLimit < 0) throw BusinessException("weeklyLimit must not be negative")
        return override(ownerId, weeklyLimit, unlimited = false)
    }

    /** Give [ownerId] unlimited turns; a confirmed email is still required. */
    @Transactional
    fun setUnlimited(ownerId: String): QuotaStatus = override(ownerId, weeklyLimit = 0, unlimited = true)

    private fun override(
        ownerId: String,
        weeklyLimit: Int,
        unlimited: Boolean
    ): QuotaStatus {
        val row = limits.findById(ownerId).orElse(null)
        if (row == null) {
            limits.save(AgentQuotaLimit(ownerId, weeklyLimit, clock.instant(), unlimited))
        } else {
            row.weeklyLimit = weeklyLimit
            row.unlimited = unlimited
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

/** The configured [defaultLimit], and [users] keyed by `SystemUser.id`; a user absent from it is on the default. */
data class QuotaOverview(
    val defaultLimit: Int,
    val users: Map<String, QuotaStatus>
)

/**
 * One user's weekly AI quota. [unlimited] callers (`beancounter:ai`, or an
 * administrator's grant) have no limit, and [limit] and [remaining] are then zero.
 * An [override] with a [limit] of 0 has AI disabled. [contact] is who to ask for a
 * higher limit; [override] is true when an administrator set [limit].
 * [verified] is false when the caller's email address is not confirmed; a
 * metered caller is then served cached Asset Reviews only.
 */
data class QuotaStatus(
    val limit: Int,
    val used: Int,
    val remaining: Int,
    val resetAt: Instant?,
    val contact: String,
    val override: Boolean = false,
    val unlimited: Boolean = false,
    val verified: Boolean = true
)