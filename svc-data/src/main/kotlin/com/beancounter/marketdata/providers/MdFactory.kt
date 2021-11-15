package com.beancounter.marketdata.providers

import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.alpha.AlphaService
import com.beancounter.marketdata.providers.mock.CashProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
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
    alphaService: AlphaService,
    wtdService: WtdService
) {
    private val providers: MutableMap<String, MarketDataProvider> = HashMap()

    /**
     * Locate a price provider for the requested market.
     *
     * @param market who is pricing this for this market
     * @return the provider that supports the asset
     */
    @Cacheable("providers")
    fun getMarketDataProvider(market: Market): MarketDataProvider? {
        return resolveProvider(market) ?: return providers[CashProviderService.ID]
    }

    fun getMarketDataProvider(provider: String): MarketDataProvider {
        return providers[provider.uppercase(Locale.getDefault())]!!
    }

    private fun resolveProvider(market: Market): MarketDataProvider? {
        // ToDo: Map Market to Provider
        for (key in providers.keys) {
            if (providers[key]!!.isMarketSupported(market)) {
                return providers[key]
            }
        }
        log.error("Unable to identify a provider for {}", market)
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(MdFactory::class.java)
    }

    init {
        providers[cashProviderService.getId().uppercase(Locale.getDefault())] = cashProviderService
        providers[wtdService.getId().uppercase(Locale.getDefault())] = wtdService
        providers[alphaService.getId().uppercase(Locale.getDefault())] = alphaService
    }
}
