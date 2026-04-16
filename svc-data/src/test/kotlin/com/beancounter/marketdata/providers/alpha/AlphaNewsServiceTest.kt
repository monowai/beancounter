package com.beancounter.marketdata.providers.alpha

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Verify market-aware ticker formatting in AlphaNewsService.
 */
class AlphaNewsServiceTest {
    private val objectMapper = ObjectMapper()

    private val alphaConfig =
        AlphaConfig().apply {
            markets = "NASDAQ,NYSE,ASX,NZX,LON"
        }

    @Test
    fun `US tickers call gateway without suffix`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(eq("AAPL"), eq("demo"), eq(null), eq(10)))
            .thenReturn("""{"feed":[]}""")
        val service = createService(gateway)

        service.getNewsSentiment("AAPL", "NASDAQ")
        verify(gateway).getNewsSentiment(eq("AAPL"), eq("demo"), eq(null), eq(10))
    }

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
    fun `LON market short-circuits to empty without calling gateway`() {
        val gateway = mock<AlphaGateway>()
        val service = createService(gateway)

        val result = service.getNewsSentiment("HSBA", "LON")

        assertThat(result).isEmpty()
        verifyNoInteractions(gateway)
    }

    @Test
    fun `null market calls gateway with tickers unchanged`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(eq("AAPL"), eq("demo"), eq(null), eq(10)))
            .thenReturn("""{"feed":[]}""")
        val service = createService(gateway)

        service.getNewsSentiment("AAPL")
        verify(gateway).getNewsSentiment(eq("AAPL"), eq("demo"), eq(null), eq(10))
    }

    @Test
    fun `NYSE market calls gateway as US market`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getNewsSentiment(eq("GE"), eq("demo"), eq(null), eq(10)))
            .thenReturn("""{"feed":[]}""")
        val service = createService(gateway)

        service.getNewsSentiment("GE", "NYSE")
        verify(gateway).getNewsSentiment(eq("GE"), eq("demo"), eq(null), eq(10))
    }

    private fun createService(gateway: AlphaGateway): AlphaNewsService {
        val service = AlphaNewsService(gateway, alphaConfig, objectMapper)
        val field = AlphaNewsService::class.java.getDeclaredField("apiKey")
        field.isAccessible = true
        field.set(service, "demo")
        return service
    }
}