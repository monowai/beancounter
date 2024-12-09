package com.beancounter.marketdata.providers

import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import com.beancounter.marketdata.providers.marketstack.MarketStackService
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
    offMarketDataProvider: OffMarketDataProvider,
    marketStackService: MarketStackService
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
                OffMarketDataProvider.ID,
                offMarketDataProvider
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