package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Market
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.model.EodhdArticleSentiment
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import com.beancounter.marketdata.providers.eodhd.news.NewsArticle
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleRepo
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleTicker
import com.beancounter.marketdata.providers.eodhd.news.NewsFetch
import com.beancounter.marketdata.providers.eodhd.news.NewsFetchRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

/**
 * Unit tests for [EodhdNewsService] with repos mocked. End-to-end persistence is covered by the
 * Spring + WireMock integration test ([EodhdNewsApiTest]); these specs pin the routing logic, the
 * refresh-gating, and the response shape.
 */
internal class EodhdNewsServiceTest {
    private val proxy = mock<EodhdProxy>()
    private val marketService = mock<MarketService>()
    private val articleRepo = mock<NewsArticleRepo>()
    private val fetchRepo = mock<NewsFetchRepo>()
    private val props =
        EodhdNewsProperties(
            maxArticles = 3,
            providerLimit = 10,
            refreshAfterHours = 6,
            retentionDays = 30
        )
    private val config =
        EodhdConfig(marketService).also {
            ReflectionTestUtils.setField(it, "apiKey", "demo")
            ReflectionTestUtils.setField(it, "markets", "")
        }
    private val service = EodhdNewsService(proxy, config, props, articleRepo, fetchRepo)

    @Test
    fun `cache-miss triggers a refresh against EODHD for US tickers`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.empty())
        whenever(proxy.getNews(eq("AAPL.US"), any(), anyOrNull(), any())).thenReturn(listOf(eodhArticle(0.8)))
        whenever(articleRepo.findByExternalId(any())).thenReturn(Optional.empty())
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("AAPL")

        verify(proxy).getNews(eq("AAPL.US"), eq(10), eq(null), eq("demo"))
        verify(fetchRepo).save(any<NewsFetch>())
    }

    @Test
    fun `fresh fetch metadata short-circuits the upstream call`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(
            Optional.of(NewsFetch("AAPL.US", LocalDateTime.now().minusHours(2), 5))
        )
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("AAPL")

        verify(proxy, never()).getNews(any(), any(), anyOrNull(), any())
        verify(fetchRepo, never()).save(any<NewsFetch>())
    }

    @Test
    fun `routes non-US tickers via the eodhd market alias`() {
        whenever(marketService.getMarket("LON"))
            .thenReturn(Market(code = "LON", aliases = mapOf("eodhd" to "LSE")))
        whenever(fetchRepo.findById("BARC.LSE")).thenReturn(Optional.empty())
        whenever(proxy.getNews(eq("BARC.LSE"), any(), anyOrNull(), any())).thenReturn(listOf(eodhArticle(0.5)))
        whenever(articleRepo.findByExternalId(any())).thenReturn(Optional.empty())
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("BARC", market = "LON")

        verify(proxy).getNews(eq("BARC.LSE"), any(), anyOrNull(), any())
    }

    @Test
    fun `ranks stored articles by absolute polarity and truncates to maxArticles`() {
        // No upstream refresh needed — fetch row is fresh.
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(
            Optional.of(NewsFetch("AAPL.US", LocalDateTime.now().minusMinutes(30), 5))
        )
        val stored =
            listOf(
                storedArticle(polarity = 0.1, title = "Low impact"),
                storedArticle(polarity = -0.9, title = "Very bearish"),
                storedArticle(polarity = 0.6, title = "Bullish"),
                storedArticle(polarity = 0.4, title = "Mildly bullish"),
                storedArticle(polarity = -0.2, title = "Slightly bearish")
            )
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(stored)

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed).hasSize(3)
        assertThat(feed.map { it["title"] })
            .containsExactly("Very bearish", "Bullish", "Mildly bullish")
        assertThat(feed.first()["sentimentLabel"]).isEqualTo("Bearish")
        assertThat(result["count"]).isEqualTo(3)
    }

    @Test
    fun `topic filter restricts to articles carrying the matching tag`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(
            Optional.of(NewsFetch("AAPL.US", LocalDateTime.now().minusMinutes(30), 2))
        )
        val stored =
            listOf(
                storedArticle(polarity = 0.9, title = "Earnings beat", tags = setOf("EARNINGS")),
                storedArticle(polarity = 0.8, title = "Random news", tags = setOf("MARKETS"))
            )
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(stored)

        val result = service.getNewsSentiment("AAPL", topics = "earnings")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed).hasSize(1)
        assertThat(feed.first()["title"]).isEqualTo("Earnings beat")
    }

    @Test
    fun `projection caps summary length`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(
            Optional.of(NewsFetch("AAPL.US", LocalDateTime.now().minusMinutes(30), 1))
        )
        val long = "x".repeat(1000)
        whenever(articleRepo.findByTickersAfter(any(), any()))
            .thenReturn(listOf(storedArticle(polarity = 0.7, content = long)))

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat((feed.first()["summary"] as String).length).isLessThanOrEqualTo(400)
    }

    @Test
    fun `dedup upsert reuses an existing article row keyed by externalId`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.empty())
        val incoming = eodhArticle(0.6, link = "https://example.com/news/123")
        whenever(proxy.getNews(eq("AAPL.US"), any(), anyOrNull(), any())).thenReturn(listOf(incoming))
        val existing =
            NewsArticle(
                externalId = "https://example.com/news/123",
                title = "old title",
                content = "old"
            )
        whenever(articleRepo.findByExternalId(eq("https://example.com/news/123")))
            .thenReturn(Optional.of(existing))
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("AAPL")

        val captor = argumentCaptor<NewsArticle>()
        verify(articleRepo).save(captor.capture())
        // The same instance is reused (id preserved), with content fields overwritten by the
        // upstream payload — this is the contract that prevents duplicate rows on refresh.
        assertThat(captor.firstValue.id).isEqualTo(existing.id)
        assertThat(captor.firstValue.title).isEqualTo(incoming.title)
    }

    private fun eodhArticle(
        polarity: Double,
        title: String = "headline",
        content: String = "body",
        tags: List<String> = emptyList(),
        link: String = "https://example.com/article"
    ): EodhdNewsArticle =
        EodhdNewsArticle(
            date = "2026-05-15T05:00:00+00:00",
            title = title,
            content = content,
            link = link,
            symbols = listOf("AAPL.US"),
            tags = tags,
            sentiment = EodhdArticleSentiment(polarity = BigDecimal(polarity))
        )

    private fun storedArticle(
        polarity: Double,
        title: String = "headline",
        content: String = "body",
        tags: Set<String> = emptySet()
    ): NewsArticle =
        NewsArticle(
            externalId = "ext-${title.hashCode()}",
            published = LocalDateTime.now().minusHours(1),
            title = title,
            content = content,
            polarity = BigDecimal(polarity),
            tags = tags.toMutableSet(),
            tickerLinks = mutableSetOf()
        ).also {
            it.tickerLinks.add(NewsArticleTicker(ticker = "AAPL.US"))
        }
}