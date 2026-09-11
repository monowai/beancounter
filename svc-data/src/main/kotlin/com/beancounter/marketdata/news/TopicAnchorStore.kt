package com.beancounter.marketdata.news

import org.springframework.stereotype.Component

/**
 * Anchor-phrase vectors for derived-topic tagging (see [TopicAnchors]), embedded once and cached
 * for the process lifetime. `by lazy`'s default [LazyThreadSafetyMode.SYNCHRONIZED] covers
 * concurrent first-use from [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s ingest
 * path — the embed call only fires on the first topic-match request, not at construction, so
 * wiring this bean is free when the embedder is inactive (the default) and it's simply never used.
 */
@Component
class TopicAnchorStore(
    private val embedder: NewsEmbedder
) {
    private val anchors: List<Pair<String, FloatArray>> by lazy {
        val topics = TopicAnchors.PHRASES.keys.toList()
        val vectors = embedder.embed(TopicAnchors.PHRASES.values.toList())
        topics.zip(vectors)
    }

    /** Topics whose anchor phrase cosine-matches [vector] at or above [threshold]. */
    fun matchingTopics(
        vector: FloatArray,
        threshold: Double
    ): Set<String> =
        anchors
            .asSequence()
            .filter { (_, anchorVector) -> VectorMath.dot(vector, anchorVector) >= threshold }
            .map { it.first }
            .toSet()
}