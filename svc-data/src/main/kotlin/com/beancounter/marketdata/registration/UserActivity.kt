package com.beancounter.marketdata.registration

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** When the SystemUser [systemUserId] was last seen. */
@Entity
@Table(name = "user_activity")
class UserActivity(
    @Id
    val systemUserId: String,
    @Column(nullable = false)
    var lastSeen: Instant
)

interface UserActivityRepository : CrudRepository<UserActivity, String>

/**
 * Records that a user was seen. Written at most once an [INTERVAL], so a
 * busy session costs one write an hour, not one per request.
 */
@Service
class UserActivityService(
    private val activity: UserActivityRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    // Not @Transactional: seenQuietly calls it on this instance, past any proxy;
    // each repository call runs in its own transaction.
    fun seen(systemUserId: String) {
        val now = clock.instant()
        val row = activity.findById(systemUserId).orElse(null)
        if (row == null) {
            activity.save(UserActivity(systemUserId, now))
        } else if (row.lastSeen.plus(INTERVAL) <= now) {
            row.lastSeen = now
            activity.save(row)
        }
    }

    /** Every user's last-seen instant, by SystemUser.id. */
    @Transactional(readOnly = true)
    fun lastSeen(): Map<String, Instant> = activity.findAll().associate { it.systemUserId to it.lastSeen }

    /** [seen], logging rather than failing: a missed stamp must never fail the caller's request. */
    fun seenQuietly(systemUserId: String) {
        try {
            seen(systemUserId)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Could not record activity for {}: {}", systemUserId, e.message)
        }
    }

    companion object {
        val INTERVAL: Duration = Duration.ofHours(1)
        private val log = LoggerFactory.getLogger(UserActivityService::class.java)
    }
}