package com.beancounter.agent.review

import com.beancounter.agent.conversation.TurnRecorder
import org.slf4j.LoggerFactory

/**
 * Records a canned Asset Review as [delegate] would, and keeps a successful
 * answer in [cache] under [key]. Only [answered] stores: the controller calls
 * it for a complete, non-blank answer and never for a failed or abandoned
 * turn. A failure to store is logged, never allowed to cost the answer.
 */
class ReviewCachingRecorder(
    private val delegate: TurnRecorder,
    private val cache: AssetReviewCache,
    private val key: AssetReviewKey
) : TurnRecorder {
    private val log = LoggerFactory.getLogger(ReviewCachingRecorder::class.java)

    /** Caches only once the turn is recorded: an answer the conversation lacks is not shared. */
    override fun answered(content: String) {
        delegate.answered(content)
        store(content)
    }

    override fun failed(code: String) = delegate.failed(code)

    private fun store(content: String) {
        try {
            cache.store(key, content)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Failed to cache asset review {}:{}: {}", key.market, key.ticker, e.message)
        }
    }
}