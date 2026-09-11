package com.beancounter.marketdata.macro

import com.beancounter.marketdata.providers.alpha.AlphaGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [TreasuryYieldFetcher] — the cached AlphaVantage TREASURY_YIELD parse layer
 * shared by [TreasuryYieldService] and [MacroRefreshSchedule].
 */
class TreasuryYieldFetcherTest {
    private val objectMapper = ObjectMapper()

    private fun createFetcher(gateway: AlphaGateway): TreasuryYieldFetcher {
        val fetcher = TreasuryYieldFetcher(gateway, objectMapper)
        val field = TreasuryYieldFetcher::class.java.getDeclaredField("apiKey")
        field.isAccessible = true
        field.set(fetcher, "demo")
        return fetcher
    }

    @Test
    fun `fetch requests daily interval for the given maturity`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getTreasuryYield(any(), any(), any())).thenReturn("""{"data":[]}""")

        createFetcher(gateway).fetch("10year")

        verify(gateway).getTreasuryYield(eq("daily"), eq("10year"), eq("demo"))
    }

    @Test
    fun `non-trading-day dot values are filtered out`() {
        val raw =
            """
            {"name":"10-Year Treasury","data":[
              {"date":"2026-09-09","value":"4.83"},
              {"date":"2026-09-07","value":"."},
              {"date":"2026-09-06","value":"4.79"}
            ]}
            """.trimIndent()
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getTreasuryYield(any(), any(), any())).thenReturn(raw)

        val points = createFetcher(gateway).fetch("10year")

        assertThat(points).hasSize(2)
        assertThat(points.map { it.date }).containsExactlyInAnyOrder(
            LocalDate.of(2026, 9, 9),
            LocalDate.of(2026, 9, 6)
        )
        assertThat(points.first { it.date == LocalDate.of(2026, 9, 9) }.value)
            .isEqualByComparingTo(BigDecimal("4.83"))
    }

    @Test
    fun `blank upstream response returns an empty list rather than throwing`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getTreasuryYield(any(), any(), any())).thenReturn("")

        val points = createFetcher(gateway).fetch("2year")

        assertThat(points).isEmpty()
    }

    @Test
    fun `malformed upstream json returns an empty list rather than throwing`() {
        val gateway = mock<AlphaGateway>()
        whenever(gateway.getTreasuryYield(any(), any(), any())).thenReturn("not json")

        val points = createFetcher(gateway).fetch("2year")

        assertThat(points).isEmpty()
    }
}