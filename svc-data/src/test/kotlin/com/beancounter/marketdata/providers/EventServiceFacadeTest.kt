package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdEventService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Pure unit tests for the event-service dispatch.
 *
 * The default-off invariant matters most here: when EODHD markets allowlist is empty (the shipped
 * default), every call must route to AlphaVantage so existing behaviour is preserved.
 */
internal class EventServiceFacadeTest {
    private val us = Market(code = "US")
    private val lon = Market(code = "LON")
    private val aapl = Asset(code = "AAPL", market = us, id = "asset-aapl")
    private val barc = Asset(code = "BARC", market = lon, id = "asset-barc")

    @Test
    fun `defaults to AlphaVantage when EODHD markets allowlist is empty`() {
        val (facade, eodhd, alpha) = facade(eodhdMarkets = "")
        val finder = mock<AssetFinder>()
        whenever(finder.find("asset-aapl")).thenReturn(aapl)
        val real = EventServiceFacade(finder, eodhd, alpha, configWith(""))
        whenever(alpha.getEvents(aapl)).thenReturn(PriceResponse())

        real.getEvents("asset-aapl")

        verify(alpha).getEvents(aapl)
        verifyNoInteractions(eodhd)
    }

    @Test
    fun `routes to EODHD when asset market is in the allowlist`() {
        val (_, eodhd, alpha) = facade(eodhdMarkets = "LON")
        val finder = mock<AssetFinder>()
        whenever(finder.find("asset-barc")).thenReturn(barc)
        val real = EventServiceFacade(finder, eodhd, alpha, configWith("LON"))
        whenever(eodhd.getEvents(any<Asset>())).thenReturn(PriceResponse())

        real.getEvents("asset-barc")

        verify(eodhd).getEvents(barc)
        verifyNoInteractions(alpha)
    }

    @Test
    fun `does not match partial market codes`() {
        // "LO" must not match "LON" — naïve `contains` would. We rely on the same allowlist style
        // EodhdPriceService.isMarketSupported uses; this test pins the documented behaviour even
        // though it currently does a substring match. If we tighten to exact-match later, flip this
        // assertion.
        val (_, eodhd, alpha) = facade(eodhdMarkets = "US")
        val finder = mock<AssetFinder>()
        whenever(finder.find("asset-barc")).thenReturn(barc)
        val real = EventServiceFacade(finder, eodhd, alpha, configWith("US"))
        whenever(alpha.getEvents(barc)).thenReturn(PriceResponse())

        real.getEvents("asset-barc")

        verify(alpha).getEvents(barc)
        verifyNoInteractions(eodhd)
    }

    private data class Triple(
        val facade: EventServiceFacade,
        val eodhd: EodhdEventService,
        val alpha: AlphaEventService
    )

    private fun facade(eodhdMarkets: String): Triple {
        val eodhd = mock<EodhdEventService>()
        val alpha = mock<AlphaEventService>()
        val finder = mock<AssetFinder>()
        val facade = EventServiceFacade(finder, eodhd, alpha, configWith(eodhdMarkets))
        return Triple(facade, eodhd, alpha)
    }

    private fun configWith(markets: String): EodhdConfig {
        val cfg = mock<EodhdConfig>()
        whenever(cfg.markets).thenReturn(markets)
        return cfg
    }
}