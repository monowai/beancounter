package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils

/**
 * Pure unit tests for [EodhdConfig] — no Spring context.
 *
 * These pin behaviour that has to hold regardless of how the rest of the marketdata module is wired:
 * the disabled-by-default invariant and the ticker-suffix composition for known markets.
 */
internal class EodhdConfigTest {
    @Test
    fun `getPriceCode appends the eodhd alias to the asset code`() {
        val lon = Market(code = "LON", aliases = mapOf("eodhd" to "LSE"))
        val ms = mock<MarketService>()
        whenever(ms.getMarket("LON")).thenReturn(lon)

        val cfg = EodhdConfig(ms)
        val asset = Asset(code = "BARC", market = lon)

        assertThat(cfg.getPriceCode(asset)).isEqualTo("BARC.LSE")
    }

    @Test
    fun `getPriceCode falls back to the bare asset code when no alias is configured`() {
        val unknown = Market(code = "WEIRD")
        val ms = mock<MarketService>()
        whenever(ms.getMarket("WEIRD")).thenReturn(unknown)

        val cfg = EodhdConfig(ms)
        val asset = Asset(code = "FOO", market = unknown)

        assertThat(cfg.getPriceCode(asset)).isEqualTo("FOO")
    }

    @Test
    fun `getPriceCode honours an explicit priceSymbol override on the asset`() {
        val lon = Market(code = "LON", aliases = mapOf("eodhd" to "LSE"))
        val ms = mock<MarketService>()
        val cfg = EodhdConfig(ms)
        val asset =
            Asset(
                code = "BARC",
                market = lon,
                priceSymbol = "BARC.OVERRIDE"
            )

        assertThat(cfg.getPriceCode(asset)).isEqualTo("BARC.OVERRIDE")
    }

    @Test
    fun `default config is opt-in — empty markets allowlist keeps the provider unrouted`() {
        val cfg = EodhdConfig(mock())
        ReflectionTestUtils.setField(cfg, "apiKey", "demo")
        ReflectionTestUtils.setField(cfg, "markets", "")
        val proxy = mock<EodhdProxy>()
        val adapter = mock<EodhdAdapter>()
        val service =
            EodhdPriceService(
                cfg,
                proxy,
                adapter,
                com.beancounter.common.utils
                    .DateUtils()
            )

        assertThat(service.isMarketSupported(Market("US"))).isFalse
        assertThat(service.isMarketSupported(Market("LON"))).isFalse
    }
}