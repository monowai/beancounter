package com.beancounter.agent.review

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * The [AssetReviewCache] in the agent database, so cached reviews survive
 * restarts and deploys. A review is served only on its own [AssetReviewKey.reviewDate]
 * and while younger than `beancounter.agent.review-cache.max-age`. After each
 * store, reviews dated more than [KEEP_DAYS] days back are cleared. Reads are
 * plain repository reads; every write runs in a transaction of its own. Two first requests
 * for one review can race to insert it; the loser overwrites the winner's row.
 */
@Service
class AssetReviewCacheService(
    private val repository: AssetReviewRepository,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
    @Value($$"${beancounter.agent.review-cache.enabled:true}") override val enabled: Boolean,
    @Value($$"${beancounter.agent.review-cache.max-age:PT8H}") private val maxAge: Duration
) : AssetReviewCache {
    companion object {
        const val KEEP_DAYS = 3L
    }

    private val log = LoggerFactory.getLogger(AssetReviewCacheService::class.java)

    // Its own transaction even when a caller has one open, so a lost insert race
    // rolls back only this write and the retry starts clean.
    private val transaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    @Transactional(readOnly = true)
    override fun find(key: AssetReviewKey): String? {
        if (!enabled) return null
        val review = existing(key) ?: return null
        if (review.createdAt.isBefore(clock.instant().minus(maxAge))) return null
        return review.content
    }

    override fun store(
        key: AssetReviewKey,
        content: String
    ) {
        if (!enabled || content.isBlank()) return
        try {
            transaction.executeWithoutResult { write(key, content) }
        } catch (e: DataIntegrityViolationException) {
            // Retry only a lost insert race: the other request's row now exists to update.
            // Anything else is a real data error and propagates.
            if (transaction.execute { existing(key) } == null) throw e
            transaction.executeWithoutResult { write(key, content) }
        }
        purge(key)
    }

    /** Best effort, after the write: clearing old days never costs the review just stored. */
    private fun purge(key: AssetReviewKey) {
        val cutoff = key.reviewDate.minusDays(KEEP_DAYS)
        try {
            transaction.executeWithoutResult { repository.deleteReviewedBefore(cutoff) }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Failed to purge asset reviews before {}: {}", cutoff, e.message)
        }
    }

    private fun write(
        key: AssetReviewKey,
        content: String
    ) {
        val now = clock.instant()
        val review = existing(key)
        if (review == null) {
            // Flushed here so a lost insert race surfaces inside this transaction.
            repository.saveAndFlush(
                AssetReview(
                    market = key.market,
                    ticker = key.ticker,
                    reviewDate = key.reviewDate,
                    model = key.model,
                    content = content,
                    createdAt = now
                )
            )
        } else {
            // Last writer wins: a refresh, an expired review, or the loser of an insert race.
            review.content = content
            review.createdAt = now
        }
    }

    private fun existing(key: AssetReviewKey): AssetReview? =
        repository.findByMarketAndTickerAndReviewDateAndModel(
            key.market,
            key.ticker,
            key.reviewDate,
            key.model
        )
}