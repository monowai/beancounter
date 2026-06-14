package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.model.EodhdSearchResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

    // BC splits London into LON (GBP, pence-quoted) and LSE (USD ETFs); both carry the same
    // EODHD alias ("LSE"). US is the unambiguous control. The currency-aware resolver disambiguates
    // the London pair on EODHD's reported instrument currency.
    private val lon = Market(code = "LON", currencyId = "GBP", aliases = mapOf("eodhd" to "LSE"))
    private val lse = Market(code = "LSE", currencyId = "USD", aliases = mapOf("eodhd" to "LSE"))
    private val us = Market(code = "US", currencyId = "USD")

    @BeforeEach
    fun stubMarketMap() {
        val markets = mapOf("LON" to lon, "LSE" to lse, "US" to us)
        whenever(marketService.getMarketMap()).thenReturn(markets)
        // canonical() only collapses the inactive US aggregator codes; for these
        // active markets it is identity, mirroring the real MarketService.
        whenever(marketService.canonical(any())).thenAnswer { inv ->
            markets.getValue(inv.getArgument<String>(0))
        }
    }

    private fun configWithMarkets(markets: String): EodhdConfig =
        EodhdConfig(marketService).also {
            ReflectionTestUtils.setField(it, "apiKey", "demo")
            ReflectionTestUtils.setField(it, "markets", markets)
        }

    private fun searchResult(
        code: String,
        exchange: String,
        currency: String?,
        name: String = code,
        type: String = "ETF"
    ) = EodhdSearchResult(
        code = code,
        exchange = exchange,
        name = name,
        type = type,
        country = "XX",
        currency = currency,
        isin = null
    )

    @Test
    fun `searchAssets returns mapped results from EODHD search`() {
        val service = EodhdPriceService(configWithMarkets("US"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "HYSA", exchange = "US", currency = "USD", name = HYSA_NAME))
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
    fun `searchAssets routes a USD London listing to the LSE market`() {
        // VUAA/MOTU et al — USD-denominated London ETFs key to LSE (currencyId USD).
        val service = EodhdPriceService(configWithMarkets("LON,LSE"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "MOTU", exchange = "LSE", currency = "USD", name = MOTU_NAME))
        )

        val results = service.searchAssets("MOTU", null)

        assertThat(results).hasSize(1)
        assertThat(results.first().market).isEqualTo("LSE")
        assertThat(results.first().symbol).isEqualTo("MOTU")
        assertThat(results.first().currency).isEqualTo("USD")
    }

    @Test
    fun `searchAssets routes a GBX London listing to the LON market`() {
        // EODHD reports London pence as GBX; BC's LON market is currencyId GBP (0.01 multiplier).
        val service = EodhdPriceService(configWithMarkets("LON,LSE"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "VOD", exchange = "LSE", currency = "GBX"))
        )

        val results = service.searchAssets("VOD", null)

        assertThat(results).hasSize(1)
        assertThat(results.first().market).isEqualTo("LON")
        assertThat(results.first().currency).isEqualTo("GBX")
    }

    @Test
    fun `searchAssets routes a GBP London listing to the LON market`() {
        val service = EodhdPriceService(configWithMarkets("LON,LSE"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "TSCO", exchange = "LSE", currency = "GBP"))
        )

        val results = service.searchAssets("TSCO", null)

        assertThat(results).hasSize(1)
        assertThat(results.first().market).isEqualTo("LON")
        assertThat(results.first().currency).isEqualTo("GBP")
    }

    @Test
    fun `searchAssets resolves an unambiguous exchange regardless of currency`() {
        // US maps to a single BC market; the currency must not affect routing.
        val service = EodhdPriceService(configWithMarkets("US"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "HYSA", exchange = "US", currency = "EUR", name = HYSA_NAME))
        )

        val results = service.searchAssets("HYSA", "US")

        assertThat(results).hasSize(1)
        assertThat(results.first().market).isEqualTo("US")
        assertThat(results.first().currency).isEqualTo("EUR")
    }

    @Test
    fun `searchAssets drops rows whose exchange maps to no BC market`() {
        // An EODHD exchange with no BC market (e.g. XETRA) must be dropped, not surfaced with a raw
        // code the UI would post and 404 on.
        val service = EodhdPriceService(configWithMarkets("LON"), proxy, adapter, dateUtils)
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "XYZ", exchange = "XETRA", currency = "EUR", name = "Some Frankfurt Listing"))
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
        whenever(proxy.searchAssets(any(), any())).thenReturn(
            listOf(searchResult(code = "HYSA", exchange = "US", currency = "USD"))
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