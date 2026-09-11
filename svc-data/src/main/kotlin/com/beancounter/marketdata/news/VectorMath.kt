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

    /** Dot product of two vectors — equivalent to cosine similarity when both are unit length. */
    fun dot(
        a: FloatArray,
        b: FloatArray
    ): Double {
        var sum = 0.0
        val n = minOf(a.size, b.size)
        for (i in 0 until n) sum += a[i].toDouble() * b[i].toDouble()
        return sum
    }
}