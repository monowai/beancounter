package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsFetch
import com.beancounter.marketdata.news.NewsFetchRepo
import com.beancounter.marketdata.news.embedding.NewsEmbeddingProperties
import com.beancounter.marketdata.news.embedding.NoopNewsEmbedder
import com.beancounter.marketdata.news.embedding.TopicAnchorStore
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

/**
 * [EodhdNewsRefresh] fetches only stale keys and records an outcome for every key it fetched,
 * failed or not.
 */
internal class EodhdNewsRefreshTest {
    private val proxy = mock<EodhdProxy>()
    private val articleRepo = mock<NewsArticleRepo>()
    private val fetchRepo = mock<NewsFetchRepo>()
    private val config =
        EodhdConfig(mock<MarketService>()).also {
            ReflectionTestUtils.setField(it, "apiKey", "demo")
        }
    private val embedder = NoopNewsEmbedder()
    private val refresh =
        EodhdNewsRefresh(
            proxy,
            config,
            EodhdNewsProperties(),
            fetchRepo,
            EodhdNewsIngest(
                articleRepo,
                fetchRepo,
                NewsArticleEmbeddings(embedder, NewsEmbeddingProperties(), TopicAnchorStore(embedder))
            )
        )

    @Test
    fun `a fresh key is skipped while a failed and a fetched key each record their outcome`() {
        whenever(fetchRepo.findById("FRESH.US"))
            .thenReturn(Optional.of(NewsFetch("FRESH.US", LocalDateTime.now(), 3)))
        whenever(fetchRepo.findById("FAIL.US")).thenReturn(Optional.empty())
        whenever(fetchRepo.findById("TOPIC:ECONOMY")).thenReturn(Optional.empty())
        whenever(proxy.getNews(eq("FAIL.US"), any(), anyOrNull(), any())).thenThrow(RuntimeException("429"))
        whenever(proxy.getNewsByTopic(eq("economy"), any(), any(), any()))
            .thenReturn(listOf(EodhdNewsArticle(link = "https://example.com/economy", title = "Economy")))

        refresh.refreshStale(listOf("FRESH.US", "FAIL.US", "TOPIC:ECONOMY"), mapOf("TOPIC:ECONOMY" to "Economy"))

        val fetches = argumentCaptor<NewsFetch>().also { verify(fetchRepo, atLeast(1)).save(it.capture()) }.allValues
        assertThat(fetches.associate { it.ticker to it.articlesFound })
            .containsExactlyInAnyOrderEntriesOf(mapOf("FAIL.US" to 0, "TOPIC:ECONOMY" to 1))
        val article = argumentCaptor<NewsArticle>().also { verify(articleRepo).save(it.capture()) }.firstValue
        assertThat(article.tickerLinks.map { it.ticker }).containsExactly("TOPIC:ECONOMY")
    }
}