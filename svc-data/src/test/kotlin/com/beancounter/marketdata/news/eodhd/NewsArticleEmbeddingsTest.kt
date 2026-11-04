package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.embedding.EmbeddingCodec
import com.beancounter.marketdata.news.embedding.NewsEmbedder
import com.beancounter.marketdata.news.embedding.NewsEmbeddingProperties
import com.beancounter.marketdata.news.embedding.NoopNewsEmbedder
import com.beancounter.marketdata.news.embedding.TopicAnchorStore
import com.beancounter.marketdata.news.embedding.TopicAnchors
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * [NewsArticleEmbeddings] decides which incoming articles get a fresh vector and writes it onto
 * the stored row. The embedder is a fake that returns a fixed list of vectors.
 */
internal class NewsArticleEmbeddingsTest {
    private class FixedEmbedder(
        private val vectors: List<FloatArray>
    ) : NewsEmbedder {
        override val modelId = "test-model"
        override val active = true

        override fun embed(texts: List<String>): List<FloatArray> = vectors
    }

    private fun embeddings(embedder: NewsEmbedder) =
        NewsArticleEmbeddings(embedder, NewsEmbeddingProperties(), TopicAnchorStore(embedder))

    private val fresh =
        listOf(
            "ext-1" to EodhdNewsArticle(title = "First"),
            "ext-2" to EodhdNewsArticle(title = "Second")
        )

    @Test
    fun `each fresh vector is keyed to its own article and stamped with the model`() {
        val prepared =
            embeddings(
                FixedEmbedder(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
            ).prepare(fresh, emptyMap())

        assertThat(prepared.keys).containsExactlyInAnyOrder("ext-1", "ext-2")
        assertThat(prepared.getValue("ext-2").vector).containsExactly(0f, 1f)
        assertThat(prepared.getValue("ext-2").model).isEqualTo("test-model")
    }

    @Test
    fun `a batch that returns the wrong number of vectors is discarded`() {
        val prepared = embeddings(FixedEmbedder(listOf(floatArrayOf(1f, 0f)))).prepare(fresh, emptyMap())

        assertThat(prepared).isEmpty()
    }

    @Test
    fun `an inactive embedder prepares nothing`() {
        val stored = NewsArticle(externalId = "ext-1", published = LocalDateTime.now(), fetchedAt = LocalDateTime.now())

        val prepared = embeddings(NoopNewsEmbedder()).prepare(fresh, mapOf("ext-1" to stored))

        assertThat(prepared).isEmpty()
    }

    @Test
    fun `a fresh vector replaces the stored one and re-derives topics`() {
        val anchors = TopicAnchors.PHRASES.keys.map { floatArrayOf(1f, 0f) }
        val article =
            NewsArticle(
                externalId = "ext-1",
                published = LocalDateTime.now(),
                fetchedAt = LocalDateTime.now(),
                derivedTopics = mutableSetOf("STALE")
            )

        embeddings(FixedEmbedder(anchors)).applyTo(
            article,
            NewsArticleEmbeddings.ArticleEmbedding(floatArrayOf(1f, 0f), "test-model"),
            textChanged = false
        )

        assertThat(article.embedding).containsExactly(*EmbeddingCodec.encode(floatArrayOf(1f, 0f)))
        assertThat(article.embeddingModel).isEqualTo("test-model")
        assertThat(article.derivedTopics).containsExactlyInAnyOrderElementsOf(TopicAnchors.PHRASES.keys)
    }

    /** Active embedder that records the texts it is asked to embed. */
    private class RecordingEmbedder : NewsEmbedder {
        val texts = mutableListOf<String>()
        override val modelId = "test-model"
        override val active = true

        override fun embed(texts: List<String>): List<FloatArray> {
            this.texts.addAll(texts)
            return texts.map { floatArrayOf(1f, 0f) }
        }
    }

    private fun storedEmbedded(
        title: String,
        content: String
    ) = NewsArticle(
        externalId = "ext-1",
        published = LocalDateTime.now(),
        fetchedAt = LocalDateTime.now(),
        title = title,
        content = content,
        summary = "stale summary",
        embedding = EmbeddingCodec.encode(floatArrayOf(0f, 1f)),
        embeddingModel = "test-model"
    )

    private fun prepareOne(
        embedder: RecordingEmbedder,
        incoming: EodhdNewsArticle,
        stored: NewsArticle
    ) = NewsArticleEmbeddings(embedder, NewsEmbeddingProperties(), TopicAnchorStore(NoopNewsEmbedder()))
        .prepare(listOf("ext-1" to incoming), mapOf("ext-1" to stored))

    @Test
    fun `a content-only revision is re-embedded from the incoming title and content`() {
        val embedder = RecordingEmbedder()

        val prepared =
            prepareOne(
                embedder,
                EodhdNewsArticle(title = "Headline", content = "revised body"),
                storedEmbedded(title = "Headline", content = "original body")
            )

        assertThat(prepared.keys).containsExactly("ext-1")
        assertThat(embedder.texts).containsExactly("Headline. revised body")
    }

    @Test
    fun `an unchanged article keeps its stored vector`() {
        val embedder = RecordingEmbedder()

        val prepared =
            prepareOne(
                embedder,
                EodhdNewsArticle(title = "Headline", content = "body"),
                storedEmbedded(title = "Headline", content = "body")
            )

        assertThat(prepared).isEmpty()
        assertThat(embedder.texts).isEmpty()
    }
}