package com.beancounter.agent.tools

import com.beancounter.agent.clients.AlphaVantageNewsClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

/**
 * Verify that NewsTools delegates to AlphaVantageNewsClient correctly.
 */
class NewsToolsTest {
    private val sampleResponse: Map<String, Any> =
        mapOf(
            "feed" to
                listOf(
                    mapOf(
                        "title" to "VOO hits all-time high",
                        "summary" to "Vanguard S&P 500 ETF reaches record levels",
                        "overall_sentiment_label" to "Bullish"
                    )
                )
        )

    @Test
    fun `getNews delegates to the client with tickers`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getNewsSentiment("VOO", null, null) } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        val result = tools.getNews("VOO")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getNewsSentiment("VOO", null, null)
    }

    @Test
    fun `getNews passes market and topics when provided`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getNewsSentiment("GNE", "NZX", null) } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        val result = tools.getNews("GNE", "NZX")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getNewsSentiment("GNE", "NZX", null)
    }

    @Test
    fun `getNews passes topics when provided`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getNewsSentiment("AAPL", null, "earnings") } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        val result = tools.getNews("AAPL", topics = "earnings")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getNewsSentiment("AAPL", null, "earnings")
    }

    @Test
    fun `getNews returns no_coverage marker when client returns empty map`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getNewsSentiment("GNE", "NZX", null) } doReturn emptyMap()
            }
        val tools = NewsTools(client)

        val result = tools.getNews("GNE", "NZX")

        assertThat(result["status"]).isEqualTo("no_coverage")
        assertThat(result["tickers"]).isEqualTo("GNE")
        assertThat(result["message"]).isNotNull()
    }

    @Test
    fun `getNews returns no_coverage marker when feed is empty`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getNewsSentiment("GNE", "NZX", null) } doReturn mapOf("feed" to emptyList<Any>())
            }
        val tools = NewsTools(client)

        val result = tools.getNews("GNE", "NZX")

        assertThat(result["status"]).isEqualTo("no_coverage")
    }

    @Test
    fun `getMarketNews maps the market scope to broad index proxies`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getMarketNews(listOf("GSPC.INDX", "DJI.INDX"), null) } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        val result = tools.getMarketNews("market")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getMarketNews(listOf("GSPC.INDX", "DJI.INDX"), null)
    }

    @Test
    fun `getMarketNews maps a sector scope to its SPDR ETF proxy`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getMarketNews(listOf("XLK.US"), null) } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        // Case-insensitive scope resolution.
        val result = tools.getMarketNews("Technology")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getMarketNews(listOf("XLK.US"), null)
    }

    @Test
    fun `getMarketNews returns unknown_scope without hitting the client for a bad scope`() {
        val client = mock<AlphaVantageNewsClient>()
        val tools = NewsTools(client)

        val result = tools.getMarketNews("crypto")

        assertThat(result["status"]).isEqualTo("unknown_scope")
        @Suppress("UNCHECKED_CAST")
        val supported = result["supportedScopes"] as List<String>
        assertThat(supported).contains("market", "technology", "energy")
        verifyNoInteractions(client)
    }

    @Test
    fun `getMarketNews returns no_coverage when the proxy yields an empty feed`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getMarketNews(listOf("GSPC.INDX", "DJI.INDX"), null) } doReturn mapOf("feed" to emptyList<Any>())
            }
        val tools = NewsTools(client)

        val result = tools.getMarketNews("market")

        assertThat(result["status"]).isEqualTo("no_coverage")
    }
}