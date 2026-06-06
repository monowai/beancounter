package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.model.EodhdSearchResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
        whenever(marketService.getMarket("US")).thenReturn(Market("US"))
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
    fun `searchAssets maps the EODHD exchange to its BC market code`() {
        // EODHD returns its own exchange ("LSE"); the result must carry the owning BC market code
        // ("LON") so the UI can POST it to /api/assets without tripping "Unable to resolve market".
        val service = EodhdPriceService(configWithMarkets("LON"), proxy, adapter, dateUtils)
        whenever(marketService.getMarket("LSE")).thenReturn(Market(code = "LON", currencyId = "USD"))
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(
                EodhdSearchResult(
                    code = "MOTU",
                    exchange = "LSE",
                    name = MOTU_NAME,
                    type = "ETF",
                    country = "UK",
                    currency = "USD",
                    isin = null
                )
            )
        )

        val results = service.searchAssets("MOTU", "LON")

        assertThat(results).hasSize(1)
        assertThat(results.first().market).isEqualTo("LON")
        assertThat(results.first().symbol).isEqualTo("MOTU")
        assertThat(results.first().currency).isEqualTo("USD")
    }

    @Test
    fun `searchAssets drops rows whose exchange maps to no BC market`() {
        // An EODHD exchange with no BC market (e.g. XETRA) must be dropped, not surfaced with a raw
        // code the UI would post and 404 on.
        val service = EodhdPriceService(configWithMarkets("LON"), proxy, adapter, dateUtils)
        whenever(marketService.getMarket("XETRA")).thenThrow(NotFoundException("Unable to resolve market code XETRA"))
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(
                EodhdSearchResult(
                    code = "XYZ",
                    exchange = "XETRA",
                    name = "Some Frankfurt Listing",
                    type = "ETF",
                    country = "DE",
                    currency = "EUR",
                    isin = null
                )
            )
        )

        val results = service.searchAssets("XYZ", "LON")

        assertThat(results).isEmpty()
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
        whenever(marketService.getMarket("US")).thenReturn(Market("US"))
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
        private const val MOTU_NAME = "VanEck Morningstar US Wide Moat UCITS ETF A"
    }

    @Test
    fun `searchAssets swallows transient errors and returns empty`() {
        val service = EodhdPriceService(configWithMarkets("US"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenThrow(RuntimeException("boom"))

        val results = service.searchAssets("HYSA", "US")

        assertThat(results).isEmpty()
    }
}