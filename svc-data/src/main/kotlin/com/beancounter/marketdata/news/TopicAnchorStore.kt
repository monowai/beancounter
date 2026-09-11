package com.beancounter.marketdata.news

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Anchor-phrase vectors for derived-topic tagging (see [TopicAnchors]), embedded on first use and
 * cached for the process lifetime. The embed call only fires on the first topic-match request, not
 * at construction, so wiring this bean is free when the embedder is inactive (the default) and it's
 * simply never used.
 *
 * Only a *complete* round is cached. [HttpNewsEmbedder] degrades to an empty list on any transport
 * error, and `by lazy` would have memoized that failure for the lifetime of the process — one
 * transient `bc-embed` outage would disable derived-topic tagging until the pod restarted. A short
 * round is discarded for a different reason: `zip` truncates silently, which would bind the
 * returned vectors onto the wrong topics. Same positional-misalignment guard that
 * [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s `computeEmbeddings` applies to article
 * batches.
 *
 * Double-checked locking on a [Volatile] field keeps the hit path lock-free once populated —
 * matching what `by lazy` gave us — while leaving a failed round free to retry.
 */
@Component
class TopicAnchorStore(
    private val embedder: NewsEmbedder
) {
    private val log = LoggerFactory.getLogger(TopicAnchorStore::class.java)

    @Volatile
    private var anchors: List<Pair<String, FloatArray>>? = null

    /** Topics whose anchor phrase cosine-matches [vector] at or above [threshold]. */
    fun matchingTopics(
        vector: FloatArray,
        threshold: Double
    ): Set<String> =
        anchors()
            .asSequence()
            .filter { (_, anchorVector) -> VectorMath.dot(vector, anchorVector) >= threshold }
            .map { it.first }
            .toSet()

    private fun anchors(): List<Pair<String, FloatArray>> {
        anchors?.let { return it }
        return synchronized(this) {
            anchors ?: embedAnchors().also { if (it.isNotEmpty()) anchors = it }
        }
    }

    private fun embedAnchors(): List<Pair<String, FloatArray>> {
        val topics = TopicAnchors.PHRASES.keys.toList()
        val vectors = embedder.embed(TopicAnchors.PHRASES.values.toList())
        if (vectors.size != topics.size) {
            log.warn(
                "news_embedding: anchor embedding returned {} vector(s) for {} topic(s) — not caching",
                vectors.size,
                topics.size
            )
            return emptyList()
        }
        return topics.zip(vectors)
    }
}