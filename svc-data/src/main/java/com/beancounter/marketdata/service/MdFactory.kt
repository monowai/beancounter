package com.beancounter.marketdata.service

import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.alpha.AlphaService
import com.beancounter.marketdata.providers.mock.MockProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Return a MarketData provider from a registered collection.
 *
 * @author mikeh
 * @since 2019-03-01
 */
@Service
class MdFactory internal constructor(
    mockProviderService: MockProviderService,
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
        return resolveProvider(market) ?: return providers[MockProviderService.ID]
    }

    fun getMarketDataProvider(provider: String): MarketDataProvider {
        return providers[provider.toUpperCase()]!!
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
        providers[mockProviderService.getId().toUpperCase()] = mockProviderService
        providers[wtdService.getId().toUpperCase()] = wtdService
        providers[alphaService.getId().toUpperCase()] = alphaService
    }
}
