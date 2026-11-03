package com.beancounter.agent.review

import com.beancounter.agent.conversation.TurnRecorder

/**
 * Records a canned Asset Review as [delegate] would, and keeps a successful
 * answer in [cache] under [key]. Only [answered] stores: the controller calls
 * it for a complete, non-blank answer and never for a failed or abandoned
 * turn. [AssetReviewCache.store] never throws, so storing never costs the answer.
 */
class ReviewCachingRecorder(
    private val delegate: TurnRecorder,
    private val cache: AssetReviewCache,
    private val key: AssetReviewKey
) : TurnRecorder {
    /** Caches only once the turn is recorded: an answer the conversation lacks is not shared. */
    override fun answered(content: String) {
        delegate.answered(content)
        cache.store(key, content)
    }

    override fun failed(code: String) = delegate.failed(code)
}