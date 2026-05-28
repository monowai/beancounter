package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.model.EodhdSearchResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils

/**
 * Unit tests for [EodhdPriceService.searchAssets]. End-to-end persistence is exercised by the
 * Spring + WireMock stack ([EodhdApiTest]); this spec pins the routing logic, the
 * empty-allowlist gate, and the response mapping shape.
 */
internal class EodhdPriceServiceSearchTest {
    private val proxy = mock<EodhdProxy>()
    private val marketService = mock<MarketService>()
    private val adapter = mock<EodhdAdapter>()
    private val dateUtils = DateUtils()

    private fun configWithMarkets(markets: String): EodhdConfig =
        EodhdConfig(marketService).also {
            ReflectionTestUtils.setField(it, "apiKey", "demo")
            ReflectionTestUtils.setField(it, "markets", markets)
        }

    @Test
    fun `searchAssets returns mapped results from EODHD search`() {
        val service = EodhdPriceService(configWithMarkets("US"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(
                EodhdSearchResult(
                    code = "HYSA",
                    exchange = "US",
                    name = HYSA_NAME,
                    type = "ETF",
                    country = "USA",
                    currency = "USD",
                    isin = "US69374H4047"
                )
            )
        )

        val results = service.searchAssets("HYSA", "US")

        assertThat(results).hasSize(1)
        val hysa = results.first()
        assertThat(hysa.symbol).isEqualTo("HYSA")
        assertThat(hysa.name).isEqualTo(HYSA_NAME)
        assertThat(hysa.market).isEqualTo("US")
        assertThat(hysa.currency).isEqualTo("USD")
        assertThat(hysa.type).isEqualTo("ETF")
    }

    @Test
    fun `searchAssets returns empty when EODHD markets allowlist is empty`() {
        val service = EodhdPriceService(configWithMarkets(""), proxy, adapter, dateUtils)

        val results = service.searchAssets("HYSA", "US")

        assertThat(results).isEmpty()
        verify(proxy, never()).searchAssets(any(), any())
    }

    @Test
    fun `searchAssets with null market still queries EODHD when provider is enabled`() {
        val service = EodhdPriceService(configWithMarkets("US"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(
                EodhdSearchResult(
                    code = "HYSA",
                    exchange = "US",
                    name = HYSA_NAME,
                    type = "ETF",
                    country = "USA",
                    currency = "USD",
                    isin = null
                )
            )
        )

        val results = service.searchAssets("HYSA", null)

        assertThat(results).extracting<String> { it.symbol }.containsExactly("HYSA")
        verify(proxy).searchAssets("HYSA", "demo")
    }

    companion object {
        private const val HYSA_NAME = "Pacer International HY Corp Bond ETF"
    }

    @Test
    fun `searchAssets swallows transient errors and returns empty`() {
        val service = EodhdPriceService(configWithMarkets("US"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenThrow(RuntimeException("boom"))

        val results = service.searchAssets("HYSA", "US")

        assertThat(results).isEmpty()
    }
}