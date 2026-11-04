package com.beancounter.marketdata.providers

import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService
import com.beancounter.marketdata.providers.fx.FxPairPriceService
import com.beancounter.marketdata.providers.marketstack.MarketStackService
import com.beancounter.marketdata.providers.morningstar.MorningstarPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Asset search follows price routing: a market is searched by the provider that prices it, and
 * an unscoped search asks every search surface in provider precedence order.
 */
internal class MdFactorySearchTest {
    private val us = Market("US")
    private val fx = Market("FX")
    private val mutf = Market("MUTF")

    private val eodhdSearch = searchProvider(EodhdPriceService.ID)
    private val fxSearch = searchProvider(FxPairPriceService.ID)

    // Bean order deliberately differs from provider precedence (FX before EODHD).
    private val factory =
        MdFactory(
            cashProviderService = priceProvider<CashProviderService>(CashProviderService.ID),
            alphaPriceService = priceProvider<AlphaPriceService>(AlphaPriceService.ID),
            privateMarketDataProvider = priceProvider<PrivateMarketDataProvider>(PrivateMarketDataProvider.ID),
            marketStackService = priceProvider<MarketStackService>(MarketStackService.ID),
            morningstarPriceService = priceProvider<MorningstarPriceService>(MorningstarPriceService.ID, mutf),
            eodhdPriceService = priceProvider<EodhdPriceService>(EodhdPriceService.ID, us),
            fxPairPriceService = priceProvider<FxPairPriceService>(FxPairPriceService.ID, fx),
            searchProviders = listOf(eodhdSearch, fxSearch)
        )

    @Test
    fun `should search a market with the provider that prices it`() {
        assertThat(factory.getSearchProvider(us)).isSameAs(eodhdSearch)
        assertThat(factory.getSearchProvider(fx)).isSameAs(fxSearch)
    }

    @Test
    fun `should have no search for a market whose provider cannot search`() {
        assertThat(factory.getSearchProvider(mutf)).isNull()
    }

    @Test
    fun `should list search surfaces in provider precedence order`() {
        assertThat(factory.getSearchProviders()).containsExactly(fxSearch, eodhdSearch)
    }

    private fun searchProvider(id: String): AssetSearchProvider = mock { on { getId() } doReturn id }

    private inline fun <reified T : MarketDataPriceProvider> priceProvider(
        id: String,
        market: Market? = null
    ): T =
        mock {
            on { getId() } doReturn id
            on { isMarketSupported(any()) } doReturn false
            if (market != null) on { isMarketSupported(market) } doReturn true
        }
}