package com.beancounter.marketdata.news

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

/**
 * [NewsArticle] is a JPA entity whose identity is its assigned [NewsArticle.id]. The data-class
 * defaults would have derived equality from every constructor property — including the
 * `ByteArray` embedding, which compares by reference and prints as `[B@1f2a3b`. These pin the
 * hand-written contract that replaced them.
 */
internal class NewsArticleTest {
    private fun article(
        title: String = "Apple beats",
        embedding: ByteArray? = null
    ) = NewsArticle(
        externalId = "https://example.com/news/1",
        published = LocalDateTime.of(2026, 5, 15, 9, 0),
        fetchedAt = LocalDateTime.of(2026, 5, 15, 9, 5),
        title = title,
        embedding = embedding
    )

    @Test
    fun `identity is the id, so a re-read row equals itself regardless of its vector instance`() {
        val stored = article(embedding = EmbeddingCodec.encode(floatArrayOf(1f, 0f)))
        // Same row loaded a second time: Hibernate assigns the persisted id by field access, and
        // the vector arrives as an equal-content but distinct ByteArray instance.
        val reread = article(embedding = EmbeddingCodec.encode(floatArrayOf(1f, 0f)))
        ReflectionTestUtils.setField(reread, "id", stored.id)

        assertThat(reread).isEqualTo(stored)
        assertThat(reread.hashCode()).isEqualTo(stored.hashCode())
        assertThat(setOf(stored, reread)).hasSize(1)
    }

    @Test
    fun `two distinct rows carrying identical content are not the same article`() {
        assertThat(article()).isNotEqualTo(article())
    }

    @Test
    fun `toString reports the article, not the raw vector bytes`() {
        val text = article(embedding = EmbeddingCodec.encode(floatArrayOf(1f, 0f))).toString()

        assertThat(text).contains("Apple beats").doesNotContain("[B@")
    }
}