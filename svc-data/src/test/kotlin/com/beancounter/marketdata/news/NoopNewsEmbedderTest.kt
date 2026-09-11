package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [NoopNewsEmbedder] is the flag-off default — pins the inert contract
 * [com.beancounter.marketdata.news.eodhd.EodhdNewsService] relies on for zero behavior change.
 */
internal class NoopNewsEmbedderTest {
    @Test
    fun `is inert — inactive, no vectors, stable model id`() {
        val embedder = NoopNewsEmbedder()

        assertThat(embedder.active).isFalse()
        assertThat(embedder.modelId).isEqualTo("noop")
        assertThat(embedder.embed(listOf("anything"))).isEmpty()
    }
}