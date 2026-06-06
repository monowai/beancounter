package com.beancounter.marketdata.providers.alpha

import com.beancounter.marketdata.providers.NewsServiceFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Thin-controller tests: [AlphaNewsController] only parses request params and delegates to
 * [NewsServiceFacade]. Pins the `/news/market` symbol split and the delegation contract.
 */
internal class AlphaNewsControllerTest {
    private val newsService = mock<NewsServiceFacade>()
    private val controller = AlphaNewsController(newsService)

    @Test
    fun `getNews delegates tickers, market and topics to the facade`() {
        val expected = mapOf<String, Any>("feed" to listOf<Any>(), "count" to 0)
        whenever(newsService.getNewsSentiment("AAPL", "US", "earnings")).thenReturn(expected)

        val result = controller.getNews("AAPL", "US", "earnings")

        assertThat(result).isSameAs(expected)
        verify(newsService).getNewsSentiment(eq("AAPL"), eq("US"), eq("earnings"))
    }

    @Test
    fun `getMarketNews splits comma-separated proxy symbols before delegating`() {
        val expected = mapOf<String, Any>("feed" to listOf<Any>(), "count" to 0)
        val symbols = listOf("GSPC.INDX", "XLK.US")
        whenever(newsService.getMarketNews(symbols, null)).thenReturn(expected)

        val result = controller.getMarketNews("GSPC.INDX,XLK.US", null)

        assertThat(result).isSameAs(expected)
        verify(newsService).getMarketNews(eq(symbols), eq(null))
    }
}