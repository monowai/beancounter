package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsFetch
import com.beancounter.marketdata.news.NewsFetchRepo
import com.beancounter.marketdata.news.embedding.NewsEmbeddingProperties
import com.beancounter.marketdata.news.embedding.NoopNewsEmbedder
import com.beancounter.marketdata.news.embedding.TopicAnchorStore
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

/**
 * [EodhdNewsIngest] persists one fetch outcome. Rows are captured as they are saved, so the specs
 * read the state that reaches the repository.
 */
internal class EodhdNewsIngestTest {
    private val articleRepo =
        mock<NewsArticleRepo>().also {
            whenever(it.findByExternalId(any())).thenReturn(Optional.empty())
        }
    private val fetchRepo = mock<NewsFetchRepo>()
    private val embedder = NoopNewsEmbedder()
    private val ingest =
        EodhdNewsIngest(
            articleRepo,
            fetchRepo,
            NewsArticleEmbeddings(embedder, NewsEmbeddingProperties(), TopicAnchorStore(embedder), articleRepo)
        )

    @Test
    fun `an article without a link is keyed by date and title and tagged with the queried symbol`() {
        val incoming =
            EodhdNewsArticle(date = "2026-05-15T05:00:00+00:00", title = "Headline", symbols = listOf("OTHER.US"))

        ingest.saveArticles("AAPL.US", listOf(incoming))

        val article = argumentCaptor<NewsArticle>().also { verify(articleRepo).save(it.capture()) }.firstValue
        assertThat(article.externalId).isEqualTo("2026-05-15T05:00:00+00:00|Headline")
        assertThat(article.tickerLinks.map { it.ticker }).containsExactlyInAnyOrder("AAPL.US", "OTHER.US")
        val fetch = argumentCaptor<NewsFetch>().also { verify(fetchRepo).save(it.capture()) }.firstValue
        assertThat(fetch.ticker).isEqualTo("AAPL.US")
        assertThat(fetch.articlesFound).isEqualTo(1)
    }

    @Test
    fun `a failed fetch advances the cooldown on the existing row and keeps its article count`() {
        val previous = NewsFetch("AAPL.US", LocalDateTime.now().minusDays(1), 7)
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.of(previous))

        ingest.saveFailedFetch("AAPL.US")

        val fetch = argumentCaptor<NewsFetch>().also { verify(fetchRepo).save(it.capture()) }.firstValue
        assertThat(fetch.articlesFound).isEqualTo(7)
        assertThat(fetch.lastFetchedAt).isAfter(LocalDateTime.now().minusMinutes(1))
    }
}