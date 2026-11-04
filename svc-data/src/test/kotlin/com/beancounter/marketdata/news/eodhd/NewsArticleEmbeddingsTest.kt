package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.embedding.EmbeddingCodec
import com.beancounter.marketdata.news.embedding.NewsEmbedder
import com.beancounter.marketdata.news.embedding.NewsEmbeddingProperties
import com.beancounter.marketdata.news.embedding.NoopNewsEmbedder
import com.beancounter.marketdata.news.embedding.TopicAnchorStore
import com.beancounter.marketdata.news.embedding.TopicAnchors
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

/**
 * [NewsArticleEmbeddings] decides which incoming articles get a fresh vector and writes it onto
 * the stored row. The embedder is a fake that returns a fixed list of vectors.
 */
internal class NewsArticleEmbeddingsTest {
    private val articleRepo =
        mock<NewsArticleRepo>().also {
            whenever(it.findByExternalId(any())).thenReturn(Optional.empty())
        }

    private class FixedEmbedder(
        private val vectors: List<FloatArray>
    ) : NewsEmbedder {
        override val modelId = "test-model"
        override val active = true

        override fun embed(texts: List<String>): List<FloatArray> = vectors
    }

    private fun embeddings(embedder: NewsEmbedder) =
        NewsArticleEmbeddings(embedder, NewsEmbeddingProperties(), TopicAnchorStore(embedder), articleRepo)

    private val fresh =
        listOf(
            "ext-1" to EodhdNewsArticle(title = "First"),
            "ext-2" to EodhdNewsArticle(title = "Second")
        )

    @Test
    fun `each fresh vector is keyed to its own article and stamped with the model`() {
        val prepared = embeddings(FixedEmbedder(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))).prepare(fresh)

        assertThat(prepared.embeddings.keys).containsExactlyInAnyOrder("ext-1", "ext-2")
        assertThat(prepared.embeddings.getValue("ext-2").vector).containsExactly(0f, 1f)
        assertThat(prepared.embeddings.getValue("ext-2").model).isEqualTo("test-model")
    }

    @Test
    fun `a batch that returns the wrong number of vectors is discarded`() {
        val prepared = embeddings(FixedEmbedder(listOf(floatArrayOf(1f, 0f)))).prepare(fresh)

        assertThat(prepared.embeddings).isEmpty()
    }

    @Test
    fun `an inactive embedder prepares nothing and loads no rows`() {
        val stored = NewsArticle(externalId = "ext-1", published = LocalDateTime.now(), fetchedAt = LocalDateTime.now())
        whenever(articleRepo.findByExternalId("ext-1")).thenReturn(Optional.of(stored))

        val prepared = embeddings(NoopNewsEmbedder()).prepare(fresh)

        assertThat(prepared.embeddings).isEmpty()
        assertThat(prepared.existing).isEmpty()
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
}