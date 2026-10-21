package com.beancounter.agent.review

import java.time.LocalDate

/**
 * What a canned Asset Review is cached under. The review reads no user data,
 * so one answer serves every caller; it is dated by the same clock that
 * stamps the prompt, and kept per [model] so the deep tier is never served
 * the standard tier's answer.
 */
data class AssetReviewKey(
    val market: String,
    val ticker: String,
    val reviewDate: LocalDate,
    val model: String
)

/** Serves and stores canned Asset Reviews. */
interface AssetReviewCache {
    /** False when reviews are never cached, so a caller need not build a key. */
    val enabled: Boolean

    /** The fresh review for [key], or null on a miss. [AssetReviewKey.model] wrote it. */
    fun find(key: AssetReviewKey): String?

    /** Keep [content] as the answer for [key], replacing any earlier one. */
    fun store(
        key: AssetReviewKey,
        content: String
    )

    companion object {
        /** No cache: every review is asked of the model. */
        val NONE =
            object : AssetReviewCache {
                override val enabled = false

                override fun find(key: AssetReviewKey): String? = null

                override fun store(
                    key: AssetReviewKey,
                    content: String
                ) = Unit
            }
    }
}