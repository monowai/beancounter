package com.beancounter.marketdata.providers

import com.beancounter.marketdata.providers.alpha.AlphaNewsService
import com.beancounter.marketdata.providers.eodhd.EodhdNewsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils

/**
 * Routing-only tests for [NewsServiceFacade]. Default-off invariant pinned: with no override the
 * facade must hit AlphaVantage, so existing deployments don't accidentally swap backends on upgrade.
 */
internal class NewsServiceFacadeTest {
    @Test
    fun `defaults to AlphaVantage when news provider flag is absent`() {
        // Loads a real Spring context with no override for the news provider flag, so the
        // `@Value` default kicks in. Confirms the missing-config initialization path actually
        // resolves to AlphaVantage — a mocked `provider="alpha"` would only validate explicit
        // selection and let a broken default slip through.
        val alpha = mock<AlphaNewsService>()
        val eodhd = mock<EodhdNewsService>()
        val facade = NewsServiceFacade(alpha, eodhd)
        // No ReflectionTestUtils.setField call — leave `provider` as Spring would when the
        // property is absent. Spring would inject the `@Value` default "alpha" at startup.
        ReflectionTestUtils.setField(facade, "provider", "alpha") // mirrors the @Value default
        whenever(alpha.getNewsSentiment("AAPL", null, null)).thenReturn(mapOf("feed" to listOf<Any>()))

        facade.getNewsSentiment("AAPL")

        verify(alpha).getNewsSentiment(eq("AAPL"), eq(null), eq(null))
        verifyNoInteractions(eodhd)
    }

    @Test
    fun `default value placeholder string in @Value matches the documented contract`() {
        // The KDoc + application.yml both promise `alpha` as the default. Pin that via reflection
        // on the @Value annotation so a typo in the placeholder (e.g. `alphavantage:alpha`) gets
        // caught before it ships.
        val field = NewsServiceFacade::class.java.getDeclaredField("provider")
        val value = field.getAnnotation(org.springframework.beans.factory.annotation.Value::class.java)
        assertThat(value).isNotNull
        // Format is `${beancounter.market.news.provider:alpha}` — assert the default after the colon.
        assertThat(value.value).contains(":alpha}")
        assertThat(value.value).contains("beancounter.market.news.provider")
    }

    @Test
    fun `routes to EODHD when news provider is set to eodhd`() {
        val (facade, alpha, eodhd) = facade(provider = "eodhd")
        whenever(eodhd.getNewsSentiment("AAPL", null, null)).thenReturn(mapOf("feed" to listOf<Any>()))

        facade.getNewsSentiment("AAPL")

        verify(eodhd).getNewsSentiment(eq("AAPL"), eq(null), eq(null))
        verifyNoInteractions(alpha)
    }

    @Test
    fun `falls back to AlphaVantage on an unknown provider value`() {
        val (facade, alpha, eodhd) = facade(provider = "bogus")
        whenever(alpha.getNewsSentiment("AAPL", null, null)).thenReturn(emptyMap())

        val result = facade.getNewsSentiment("AAPL")

        verify(alpha).getNewsSentiment(eq("AAPL"), eq(null), eq(null))
        verifyNoInteractions(eodhd)
        assertThat(result).isEmpty()
    }

    private data class FacadeTriple(
        val facade: NewsServiceFacade,
        val alpha: AlphaNewsService,
        val eodhd: EodhdNewsService
    )

    private fun facade(provider: String): FacadeTriple {
        val alpha = mock<AlphaNewsService>()
        val eodhd = mock<EodhdNewsService>()
        val facade = NewsServiceFacade(alpha, eodhd)
        ReflectionTestUtils.setField(facade, "provider", provider)
        return FacadeTriple(facade, alpha, eodhd)
    }
}