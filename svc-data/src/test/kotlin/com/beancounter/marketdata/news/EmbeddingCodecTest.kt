package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [EmbeddingCodec] round-trips a [FloatArray] through a little-endian byte blob — pins the wire
 * format persisted in [NewsArticle.embedding].
 */
internal class EmbeddingCodecTest {
    @Test
    fun `round-trips a float vector through the byte encoding`() {
        val vector = floatArrayOf(0.1f, -0.2f, 3.5f, 0f, 128.25f)

        val decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(vector))

        assertThat(decoded).containsExactly(*vector)
    }

    @Test
    fun `encodes exactly 4 bytes per float`() {
        val vector = FloatArray(384) { it.toFloat() }

        assertThat(EmbeddingCodec.encode(vector)).hasSize(384 * 4)
    }

    @Test
    fun `round-trips an empty vector`() {
        val decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(FloatArray(0)))

        assertThat(decoded).isEmpty()
    }
}