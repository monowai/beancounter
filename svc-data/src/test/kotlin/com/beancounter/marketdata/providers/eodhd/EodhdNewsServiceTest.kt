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
import org.springframework.dao.DataIntegrityViolationException
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
    fun `projection prefers persisted summary over content truncation`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(
            Optional.of(NewsFetch("AAPL.US", LocalDateTime.now().minusMinutes(30), 1))
        )
        // If `summary` is set, the bot must see it verbatim rather than the long content blob.
        whenever(articleRepo.findByTickersAfter(any(), any()))
            .thenReturn(
                listOf(
                    storedArticle(
                        polarity = 0.7,
                        content = "x".repeat(1000),
                        summary = "Apple beats Q2 expectations — guidance up 8%."
                    )
                )
            )

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed.first()["summary"]).isEqualTo("Apple beats Q2 expectations — guidance up 8%.")
    }

    @Test
    fun `blank summary still falls back to content truncation`() {
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(
            Optional.of(NewsFetch("AAPL.US", LocalDateTime.now().minusMinutes(30), 1))
        )
        whenever(articleRepo.findByTickersAfter(any(), any()))
            .thenReturn(
                listOf(
                    storedArticle(
                        polarity = 0.7,
                        content = "Apple beat Q2 expectations by a wide margin.",
                        summary = "   "
                    )
                )
            )

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed.first()["summary"]).isEqualTo("Apple beat Q2 expectations by a wide margin.")
    }

    @Test
    fun `unparseable date timestamp falls back to now instead of throwing`() {
        // EODHD has occasionally shipped articles with malformed dates; the service must not
        // crash the request — falls back to `now(UTC)` and stores the row with the rest intact.
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.empty())
        val broken =
            EodhdNewsArticle(
                date = "not-a-date",
                title = "garbage date",
                content = "body",
                link = "https://example.com/news/broken",
                symbols = listOf("AAPL.US"),
                sentiment = EodhdArticleSentiment(polarity = BigDecimal("0.2"))
            )
        whenever(proxy.getNews(eq("AAPL.US"), any(), anyOrNull(), any())).thenReturn(listOf(broken))
        whenever(articleRepo.findByExternalId(any())).thenReturn(Optional.empty())
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        // Must not throw.
        service.getNewsSentiment("AAPL")

        val captor = argumentCaptor<NewsArticle>()
        verify(articleRepo).save(captor.capture())
        assertThat(captor.firstValue.title).isEqualTo("garbage date")
        // Stamped to ~now (in UTC — service uses LocalDateTime.now(ZoneOffset.UTC) as the fallback),
        // not stuck on EPOCH or null.
        assertThat(captor.firstValue.published)
            .isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1))
    }

    @Test
    fun `LocalDateTime-shaped published (no zone offset) parses successfully`() {
        // Defensive path: the EODHD shape is always ISO-with-offset, but the parser falls through
        // to LocalDateTime.parse() before giving up — pin that branch.
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.empty())
        val noOffset =
            EodhdNewsArticle(
                date = "2026-05-15T09:00:00",
                title = "no offset",
                content = "body",
                link = "https://example.com/news/no-offset",
                symbols = listOf("AAPL.US"),
                sentiment = EodhdArticleSentiment(polarity = BigDecimal("0.2"))
            )
        whenever(proxy.getNews(eq("AAPL.US"), any(), anyOrNull(), any())).thenReturn(listOf(noOffset))
        whenever(articleRepo.findByExternalId(any())).thenReturn(Optional.empty())
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("AAPL")

        val captor = argumentCaptor<NewsArticle>()
        verify(articleRepo).save(captor.capture())
        assertThat(captor.firstValue.published).isEqualTo(LocalDateTime.of(2026, 5, 15, 9, 0, 0))
    }

    @Test
    fun `upstream failure still advances news_fetch so we don't retry-storm the quota`() {
        // Without this the catch block would leave news_fetch missing/stale, so every subsequent
        // request would re-hit EODHD immediately — exactly the quota-burn scenario this PR fixes.
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.empty())
        whenever(proxy.getNews(any(), any(), anyOrNull(), any())).thenThrow(RuntimeException("EODHD 429"))
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("AAPL")

        val captor = argumentCaptor<NewsFetch>()
        verify(fetchRepo).save(captor.capture())
        assertThat(captor.firstValue.ticker).isEqualTo("AAPL.US")
        // last_fetched_at advanced to ~now so the next refresh waits the configured backoff.
        assertThat(captor.firstValue.lastFetchedAt)
            .isAfter(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `concurrent insert race on externalId is recovered via merge into the winning row`() {
        // Simulate: read miss → save fails on the unique constraint because another transaction
        // raced ahead and inserted the same external_id → service re-reads and merges into the
        // winner. End state must be a single article with the latest payload.
        whenever(fetchRepo.findById("AAPL.US")).thenReturn(Optional.empty())
        val incoming = eodhArticle(0.6, link = "https://example.com/news/race")
        whenever(proxy.getNews(eq("AAPL.US"), any(), anyOrNull(), any())).thenReturn(listOf(incoming))

        val winner =
            NewsArticle(
                externalId = "https://example.com/news/race",
                title = "winner inserted first"
            )
        whenever(articleRepo.findByExternalId(eq("https://example.com/news/race")))
            // First call: no row exists yet (we read before save).
            // Second call (after the constraint violation): the winning row exists.
            .thenReturn(Optional.empty(), Optional.of(winner))
        // First save throws — simulates the unique-constraint collision with the racing transaction.
        whenever(articleRepo.save(any<NewsArticle>()))
            .thenThrow(DataIntegrityViolationException("uk_news_article_external"))
            .thenAnswer { it.arguments[0] }
        whenever(articleRepo.findByTickersAfter(any(), any())).thenReturn(emptyList())

        service.getNewsSentiment("AAPL")

        // Two saves expected: the failing original and the merged retry into the winner.
        val captor = argumentCaptor<NewsArticle>()
        verify(articleRepo, org.mockito.Mockito.times(2)).save(captor.capture())
        assertThat(captor.allValues).hasSize(2)
        assertThat(captor.allValues.last().id).isEqualTo(winner.id)
        assertThat(captor.allValues.last().title).isEqualTo(incoming.title)
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

        // Snapshot the id BEFORE invoking the service. `NewsArticle.id` is auto-initialised via
        // `KeyGenUtils()` so it's always non-null — capturing it here makes the round-trip explicit
        // and rules out an accidental new-entity instantiation path.
        val originalId = existing.id

        service.getNewsSentiment("AAPL")

        val captor = argumentCaptor<NewsArticle>()
        verify(articleRepo).save(captor.capture())
        // The same instance is reused (id preserved), with content fields overwritten by the
        // upstream payload — this is the contract that prevents duplicate rows on refresh.
        assertThat(captor.firstValue.id).isEqualTo(originalId)
        assertThat(captor.firstValue.id).isNotBlank()
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
        tags: Set<String> = emptySet(),
        summary: String? = null
    ): NewsArticle =
        NewsArticle(
            externalId = "ext-${title.hashCode()}",
            published = LocalDateTime.now().minusHours(1),
            title = title,
            content = content,
            summary = summary,
            polarity = BigDecimal(polarity),
            tags = tags.toMutableSet(),
            tickerLinks = mutableSetOf()
        ).also {
            it.tickerLinks.add(NewsArticleTicker(ticker = "AAPL.US"))
        }
}