package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [TopicAnchorStore] embeds [TopicAnchors.PHRASES] lazily on first use, then matches an article
 * vector against every anchor via cosine similarity. The embedder is faked with hand-built unit
 * vectors so these pin the threshold arithmetic, not a real model's output.
 */
internal class TopicAnchorStoreTest {
    @Test
    fun `matches only anchors whose cosine clears the threshold`() {
        val embedder = mock<NewsEmbedder>()
        val topics = TopicAnchors.PHRASES.keys.toList()
        // First anchor placed exactly at the article vector; every other anchor orthogonal to it.
        val anchorVectors =
            topics.mapIndexed { index, _ ->
                if (index ==
                    0
                ) {
                    floatArrayOf(1f, 0f)
                } else {
                    floatArrayOf(0f, 1f)
                }
            }
        whenever(embedder.embed(TopicAnchors.PHRASES.values.toList())).thenReturn(anchorVectors)
        val store = TopicAnchorStore(embedder)

        val matches = store.matchingTopics(floatArrayOf(1f, 0f), threshold = 0.55)

        assertThat(matches).containsExactly(topics.first())
    }

    @Test
    fun `no anchor matches when every cosine is below threshold`() {
        val embedder = mock<NewsEmbedder>()
        whenever(embedder.embed(any())).thenReturn(TopicAnchors.PHRASES.values.map { floatArrayOf(0f, 1f) })
        val store = TopicAnchorStore(embedder)

        val matches = store.matchingTopics(floatArrayOf(1f, 0f), threshold = 0.55)

        assertThat(matches).isEmpty()
    }

    @Test
    fun `anchors are embedded once and cached across calls`() {
        val embedder = mock<NewsEmbedder>()
        whenever(embedder.embed(any())).thenReturn(TopicAnchors.PHRASES.values.map { floatArrayOf(0f, 1f) })
        val store = TopicAnchorStore(embedder)

        store.matchingTopics(floatArrayOf(1f, 0f), threshold = 0.55)
        store.matchingTopics(floatArrayOf(0f, 1f), threshold = 0.55)

        verify(embedder, times(1)).embed(any())
    }

    /**
     * A failed round must not be memoized. [HttpNewsEmbedder] degrades to `emptyList()` on any
     * transport error, so caching that would let one transient bc-embed outage disable derived-topic
     * tagging for the whole process lifetime — recoverable only by restarting the pod.
     */
    @Test
    fun `retries anchor embedding after a failed round instead of caching the failure`() {
        val embedder = mock<NewsEmbedder>()
        whenever(embedder.embed(any()))
            .thenReturn(emptyList())
            .thenReturn(TopicAnchors.PHRASES.values.map { floatArrayOf(1f, 0f) })
        val store = TopicAnchorStore(embedder)

        assertThat(store.matchingTopics(floatArrayOf(1f, 0f), threshold = 0.55)).isEmpty()

        assertThat(store.matchingTopics(floatArrayOf(1f, 0f), threshold = 0.55))
            .containsExactlyInAnyOrderElementsOf(TopicAnchors.PHRASES.keys)
        verify(embedder, times(2)).embed(any())
    }

    /**
     * A short round is worse than no round: `zip` truncates silently, binding the first N anchor
     * vectors onto the wrong topics. Same positional-misalignment guard `EodhdNewsService`'s
     * `computeEmbeddings` already applies to article batches.
     */
    @Test
    fun `discards a short vector round rather than misaligning anchors`() {
        val embedder = mock<NewsEmbedder>()
        whenever(embedder.embed(any())).thenReturn(listOf(floatArrayOf(1f, 0f)))
        val store = TopicAnchorStore(embedder)

        assertThat(store.matchingTopics(floatArrayOf(1f, 0f), threshold = 0.55)).isEmpty()
    }
}