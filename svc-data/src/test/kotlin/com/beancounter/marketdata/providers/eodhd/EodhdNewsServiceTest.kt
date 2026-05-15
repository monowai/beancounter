package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Market
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.model.EodhdArticleSentiment
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

/**
 * Unit tests for [EodhdNewsService] — ticker resolution, ranking, projection, topic filter,
 * graceful-empty handling.
 */
internal class EodhdNewsServiceTest {
    private val proxy = mock<EodhdProxy>()
    private val marketService = mock<MarketService>()
    private val props = EodhdNewsProperties(maxArticles = 3, providerLimit = 10)
    private val config: EodhdConfig

    init {
        config =
            EodhdConfig(marketService).also {
                ReflectionTestUtils.setField(it, "apiKey", "demo")
                ReflectionTestUtils.setField(it, "markets", "")
            }
    }

    private val service = EodhdNewsService(proxy, config, props)

    @Test
    fun `resolves US tickers to the US exchange suffix`() {
        whenever(proxy.getNews(eq("AAPL.US"), any(), anyOrNull(), any())).thenReturn(listOf(article(0.8)))

        service.getNewsSentiment("AAPL")

        verify(proxy).getNews(eq("AAPL.US"), eq(10), eq(null), eq("demo"))
    }

    @Test
    fun `routes non-US tickers via the eodhd market alias`() {
        whenever(marketService.getMarket("LON"))
            .thenReturn(Market(code = "LON", aliases = mapOf("eodhd" to "LSE")))
        whenever(proxy.getNews(eq("BARC.LSE"), any(), anyOrNull(), any())).thenReturn(listOf(article(0.5)))

        service.getNewsSentiment("BARC", market = "LON")

        verify(proxy).getNews(eq("BARC.LSE"), any(), anyOrNull(), any())
    }

    @Test
    fun `returns empty map when no articles are returned`() {
        whenever(proxy.getNews(any(), any(), anyOrNull(), any())).thenReturn(emptyList())

        val result = service.getNewsSentiment("AAPL")

        assertThat(result).isEmpty()
    }

    @Test
    fun `ranks by absolute polarity and truncates to maxArticles`() {
        val articles =
            listOf(
                article(polarity = 0.1, title = "Low impact"),
                article(polarity = -0.9, title = "Very bearish"),
                article(polarity = 0.6, title = "Bullish"),
                article(polarity = 0.4, title = "Mildly bullish"),
                article(polarity = -0.2, title = "Slightly bearish")
            )
        whenever(proxy.getNews(any(), any(), anyOrNull(), any())).thenReturn(articles)

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed).hasSize(3)
        // Strongest |polarity| first: 0.9, 0.6, 0.4
        assertThat(feed.map { it["title"] })
            .containsExactly("Very bearish", "Bullish", "Mildly bullish")
        assertThat(feed.first()["sentimentLabel"]).isEqualTo("Bearish")
        assertThat(feed[1]["sentimentLabel"]).isEqualTo("Bullish")
        assertThat(feed[2]["sentimentLabel"]).isEqualTo("Bullish")
        assertThat(result["count"]).isEqualTo(3)
    }

    @Test
    fun `topic filter restricts to articles carrying the matching tag`() {
        val articles =
            listOf(
                article(polarity = 0.9, title = "Earnings beat", tags = listOf("EARNINGS")),
                article(polarity = 0.8, title = "Random news", tags = listOf("MARKETS"))
            )
        whenever(proxy.getNews(any(), any(), anyOrNull(), any())).thenReturn(articles)

        val result = service.getNewsSentiment("AAPL", topics = "earnings")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed).hasSize(1)
        assertThat(feed.first()["title"]).isEqualTo("Earnings beat")
    }

    @Test
    fun `projection caps summary length to keep the LLM payload compact`() {
        val longContent = "x".repeat(1000)
        whenever(proxy.getNews(any(), any(), anyOrNull(), any()))
            .thenReturn(listOf(article(polarity = 0.7, content = longContent)))

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat((feed.first()["summary"] as String).length).isLessThanOrEqualTo(400)
    }

    private fun article(
        polarity: Double,
        title: String = "headline",
        content: String = "body",
        tags: List<String> = emptyList()
    ): EodhdNewsArticle =
        EodhdNewsArticle(
            date = "2026-05-15T05:00:00+00:00",
            title = title,
            content = content,
            link = "https://example.com/article",
            symbols = listOf("AAPL.US"),
            tags = tags,
            sentiment = EodhdArticleSentiment(polarity = BigDecimal(polarity))
        )
}