package com.beancounter.agent.tools

import com.beancounter.agent.clients.AlphaVantageNewsClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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
                on { getNewsSentiment("VOO", null) } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        val result = tools.getNews("VOO")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getNewsSentiment("VOO", null)
    }

    @Test
    fun `getNews passes topics when provided`() {
        val client =
            mock<AlphaVantageNewsClient> {
                on { getNewsSentiment("AAPL", "earnings") } doReturn sampleResponse
            }
        val tools = NewsTools(client)

        val result = tools.getNews("AAPL", "earnings")

        assertThat(result).isSameAs(sampleResponse)
        verify(client).getNewsSentiment("AAPL", "earnings")
    }
}