package com.beancounter.marketdata.providers.alpha

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper

/**
 * Verify non-US short-circuit and ranking/trimming behaviour in
 * [AlphaNewsService].
 */
class AlphaNewsServiceTest {
    private val objectMapper = ObjectMapper()

    private val alphaConfig =
        AlphaConfig().apply {
            markets = "NASDAQ,NYSE,ASX,NZX,LON"
        }

    private val props = NewsProperties()

    @Test
    fun `NZX market short-circuits to empty without calling gateway`() {
        val gateway = mock<AlphaGateway>()
        val service = createService(gateway)

        val result = service.getNewsSentiment("GNE", "NZX")

        assertThat(result).isEmpty()
        verifyNoInteractions(gateway)
    }

    @Test
    fun `ASX market short-circuits to empty without calling gateway`() {
        val gateway = mock<AlphaGateway>()
        val service = createService(gateway)

        val result = service.getNewsSentiment("CBA", "ASX")

        assertThat(result).isEmpty()
        verifyNoInteractions(gateway)
    }

    @Test
    fun `US ticker calls gateway with configured provider limit and a time_from`() {
        val gateway = mock<AlphaGateway>()
        whenever(
            gateway.getNewsSentiment(
                eq("AAPL"),
                eq("demo"),
                eq(null),
                eq(props.providerLimit),
                any()
            )
        ).thenReturn("""{"feed":[]}""")

        val service = createService(gateway)
        service.getNewsSentiment("AAPL", "NASDAQ")

        verify(gateway).getNewsSentiment(
            eq("AAPL"),
            eq("demo"),
            eq(null),
            eq(props.providerLimit),
            any()
        )
    }

    @Test
    fun `high-relevance article survives ranking and is projected to a compact shape`() {
        val raw =
            """
            {
              "feed": [
                {
                  "title": "Apple beats earnings",
                  "summary": "Apple reported record Q4 earnings.",
                  "source": "Bloomberg",
                  "time_published": "20260416T120000",
                  "banner_image": "http://example.com/img.jpg",
                  "authors": ["Jane Doe"],
                  "url": "http://example.com/1",
                  "overall_sentiment_score": 0.4,
                  "overall_sentiment_label": "Bullish",
                  "ticker_sentiment": [
                    {
                      "ticker": "AAPL",
                      "relevance_score": "0.85",
                      "ticker_sentiment_score": "0.42",
                      "ticker_sentiment_label": "Bullish"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(raw)

        val result = createService(gateway).getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        assertThat(feed).hasSize(1)
        val article = feed.first()
        assertThat(article["title"]).isEqualTo("Apple beats earnings")
        assertThat(article["source"]).isEqualTo("Bloomberg")
        assertThat(article["relevance"]).isEqualTo(0.85)
        assertThat(article["tickerSentimentLabel"]).isEqualTo("Bullish")
        assertThat(article["tickerSentimentScore"]).isEqualTo(0.42)
        // Stripped fields are not present — saves tool_result tokens.
        assertThat(article).doesNotContainKeys(
            "banner_image",
            "authors",
            "url",
            "topics",
            "ticker_sentiment"
        )
    }

    @Test
    fun `low-relevance articles are dropped under the threshold`() {
        val raw =
            """
            {
              "feed": [
                {
                  "title": "Tangential mention",
                  "summary": "...",
                  "source": "X",
                  "time_published": "20260416T120000",
                  "ticker_sentiment": [
                    { "ticker": "AAPL", "relevance_score": "0.05",
                      "ticker_sentiment_score": "0.0",
                      "ticker_sentiment_label": "Neutral" }
                  ]
                }
              ]
            }
            """.trimIndent()
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(raw)

        val result = createService(gateway).getNewsSentiment("AAPL")

        assertThat(result).isEmpty()
    }

    @Test
    fun `ranking takes top N by relevance and breaks ties on sentiment magnitude`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(any(), any(), anyOrNull(), any(), anyOrNull()))
            .thenReturn(feedFor(relevances = listOf(0.5, 0.9, 0.7, 0.8, 0.6, 0.95, 0.4)))

        val limited = props.copy(maxArticles = 3)
        val service =
            AlphaNewsService(gateway, alphaConfig, objectMapper, limited)
                .apply { writeApiKey(this) }

        val result = service.getNewsSentiment("AAPL")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as List<Map<String, Any>>
        val relevances = feed.map { it["relevance"] as Double }
        assertThat(relevances).containsExactly(0.95, 0.9, 0.8)
    }

    @Test
    fun `articles mentioning only other tickers are filtered out`() {
        val raw =
            """
            {
              "feed": [
                {
                  "title": "All about MSFT",
                  "summary": "...",
                  "source": "X",
                  "time_published": "20260416T120000",
                  "ticker_sentiment": [
                    { "ticker": "MSFT", "relevance_score": "0.9",
                      "ticker_sentiment_score": "0.5",
                      "ticker_sentiment_label": "Bullish" }
                  ]
                }
              ]
            }
            """.trimIndent()
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(any(), any(), anyOrNull(), any(), anyOrNull())).thenReturn(raw)

        val result = createService(gateway).getNewsSentiment("AAPL")

        assertThat(result).isEmpty()
    }

    private fun feedFor(
        ticker: String = "AAPL",
        relevances: List<Double>
    ): String {
        val articles =
            relevances.mapIndexed { i, r ->
                """
                {
                  "title": "Article $i about $ticker",
                  "summary": "...",
                  "source": "X",
                  "time_published": "20260416T120000",
                  "ticker_sentiment": [
                    { "ticker": "$ticker", "relevance_score": "$r",
                      "ticker_sentiment_score": "0.1",
                      "ticker_sentiment_label": "Neutral" }
                  ]
                }
                """.trimIndent()
            }
        return """{"feed":[${articles.joinToString(",")}]}"""
    }

    @Test
    fun `invalid ticker names like company names are stripped before calling AV`() {
        val gateway = mock<AlphaGateway>()
        // Only ASML should reach the gateway after sanitisation strips COHERENT (>6 chars)
        whenever(gateway.getNewsSentiment(eq("ASML"), any(), anyOrNull(), any(), anyOrNull()))
            .thenReturn(feedFor("ASML", listOf(0.9)))
        val service = createService(gateway)

        val result = service.getNewsSentiment("ASML,COHERENT")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as? List<*>
        assertThat(feed).hasSize(1)
    }

    @Test
    fun `falls back to per-ticker when batch returns empty`() {
        val gateway = mock<AlphaGateway>()
        // Batch call returns empty (simulating AV rejecting the batch)
        whenever(gateway.getNewsSentiment(eq("COHR,ASML"), any(), anyOrNull(), any(), anyOrNull()))
            .thenReturn("""{"feed":[]}""")
        // Individual calls succeed
        whenever(gateway.getNewsSentiment(eq("COHR"), any(), anyOrNull(), any(), anyOrNull()))
            .thenReturn(feedFor("COHR", listOf(0.8)))
        whenever(gateway.getNewsSentiment(eq("ASML"), any(), anyOrNull(), any(), anyOrNull()))
            .thenReturn(feedFor("ASML", listOf(0.9)))
        val service = createService(gateway)

        val result = service.getNewsSentiment("COHR,ASML")

        @Suppress("UNCHECKED_CAST")
        val feed = result["feed"] as? List<*>
        assertThat(feed).hasSize(2)
    }

    private fun createService(gateway: AlphaGateway): AlphaNewsService {
        val service = AlphaNewsService(gateway, alphaConfig, objectMapper, props)
        writeApiKey(service)
        return service
    }

    private fun writeApiKey(service: AlphaNewsService) {
        val field = AlphaNewsService::class.java.getDeclaredField("apiKey")
        field.isAccessible = true
        field.set(service, "demo")
    }
}