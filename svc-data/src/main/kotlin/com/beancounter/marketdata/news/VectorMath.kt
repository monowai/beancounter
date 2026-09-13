package com.beancounter.marketdata.news

import kotlin.math.sqrt

/**
 * Vector helpers shared by [HttpNewsEmbedder] (unit-normalization at the embedder boundary),
 * [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s rank-time dedup clustering, and
 * [TopicAnchorStore]'s anchor matching. Embeddings are always normalized to unit length before
 * they're persisted, so cosine similarity reduces to a plain dot product everywhere downstream.
 */
object VectorMath {
    fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (value in vector) sumSquares += value.toDouble() * value.toDouble()
        val norm = sqrt(sumSquares)
        if (norm == 0.0) return vector
        return FloatArray(vector.size) { i -> (vector[i] / norm).toFloat() }
    }

    /**
     * Dot product of two vectors — equivalent to cosine similarity when both are unit length.
     *
     * Rejects a dimension mismatch rather than truncating to the shorter vector: a 384-dim vector
     * scored against the leading 384 dimensions of a 768-dim one yields a number that isn't a
     * cosine similarity but reads like one (typically high enough to look like a near-duplicate).
     * Mismatches are only reachable when a model change leaves stale vectors behind, and the
     * callers that can see that — [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s
     * dedup clustering and [TopicAnchorStore] — decide for themselves what it means.
     */
    fun dot(
        a: FloatArray,
        b: FloatArray
    ): Double {
        require(a.size == b.size) { "Embedding dimension mismatch: ${a.size} vs ${b.size}" }
        var sum = 0.0
        for (i in a.indices) sum += a[i].toDouble() * b[i].toDouble()
        return sum
    }
}