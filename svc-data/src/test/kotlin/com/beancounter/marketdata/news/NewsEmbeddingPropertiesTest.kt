package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Pins the DEFAULT-OFF contract: an un-configured deployment must never activate embeddings. */
internal class NewsEmbeddingPropertiesTest {
    @Test
    fun `defaults keep the feature off`() {
        val props = NewsEmbeddingProperties()

        assertThat(props.enabled).isFalse()
        assertThat(props.url).isEmpty()
        assertThat(props.modelId).isEqualTo("all-minilm-l6-v2")
        assertThat(props.similarityThreshold).isEqualTo(0.90)
        assertThat(props.topicThreshold).isEqualTo(0.55)
        assertThat(props.batchSize).isEqualTo(32)
    }
}