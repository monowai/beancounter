package com.beancounter.marketdata.providers

import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import com.beancounter.marketdata.providers.marketstack.MarketStackService
import com.beancounter.marketdata.providers.morningstar.MorningstarPriceService
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Return a MarketData provider from a registered collection.
 *
 * @author mikeh
 * @since 2019-03-01
 */
@Service
class MdFactory internal constructor(
    cashProviderService: CashProviderService,
    alphaPriceService: AlphaPriceService,
    privateMarketDataProvider: PrivateMarketDataProvider,
    marketStackService: MarketStackService,
    morningstarPriceService: MorningstarPriceService
) {
    private val providers: Map<String, MarketDataPriceProvider> =
        mapOf(
            Pair(
                CashProviderService.ID,
                cashProviderService
            ),
            Pair(
                MarketStackService.ID,
                marketStackService
            ),
            Pair(
                AlphaPriceService.ID,
                alphaPriceService
            ),
            Pair(
                PrivateMarketDataProvider.ID,
                privateMarketDataProvider
            ),
            Pair(
                MorningstarPriceService.ID,
                morningstarPriceService
            )
        )

    /**
     * Locate a price provider for the requested market.
     *
     * @param market who is pricing this for this market
     * @return the provider that supports the asset
     */
    @Cacheable("provider")
    fun getMarketDataProvider(market: Market): MarketDataPriceProvider = resolveProvider(market)

    fun getMarketDataProvider(provider: String): MarketDataPriceProvider =
        providers[provider.uppercase(Locale.getDefault())]!!

    private fun resolveProvider(market: Market): MarketDataPriceProvider {
        // ToDo: Map Market to Provider
        for (key in providers.keys) {
            if (providers[key]!!.isMarketSupported(market)) {
                return providers[key]!!
            }
        }
        log.error(
            "Unable to identify a provider for {}",
            market
        )
        return providers[CashProviderService.ID]!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(MdFactory::class.java)
    }
}