package com.beancounter.marketdata.news

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Codec for persisting a [FloatArray] embedding as a little-endian byte blob
 * ([NewsArticle.embedding], a `bytea`-compatible column). 4 bytes per float — a 384-dim
 * all-MiniLM-L6-v2 vector is 1536 bytes.
 */
object EmbeddingCodec {
    private const val BYTES_PER_FLOAT = Float.SIZE_BYTES

    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        for (value in vector) buffer.putFloat(value)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / BYTES_PER_FLOAT) { buffer.getFloat() }
    }
}