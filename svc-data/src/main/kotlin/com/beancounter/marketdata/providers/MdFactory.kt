package com.beancounter.marketdata.providers

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.cash.CashProviderService
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService
import com.beancounter.marketdata.providers.fx.FxPairPriceService
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
    morningstarPriceService: MorningstarPriceService,
    eodhdPriceService: EodhdPriceService,
    fxPairPriceService: FxPairPriceService,
    searchProviders: List<AssetSearchProvider>
) {
    private val providers: Map<String, MarketDataPriceProvider> =
        mapOf(
            Pair(
                CashProviderService.ID,
                cashProviderService
            ),
            // Currency pairs claim only the FX market, so sitting beside Cash shifts no other
            // market's routing; it wins FX over any vendor allowlist that still names FX.
            Pair(
                FxPairPriceService.ID,
                fxPairPriceService
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
            ),
            // EODHD is appended last so it never wins by iteration order — its empty-by-default
            // `markets` allowlist already makes it inert until an operator opts in, but keeping the
            // documented Cash → FX → MarketStack → Alpha → Private → Morningstar precedence intact means
            // routing changes for an existing market require an explicit config change, never a
            // bean-registration order shift.
            Pair(
                EodhdPriceService.ID,
                eodhdPriceService
            )
        )

    private val searchProviders: Map<String, AssetSearchProvider> = searchProviders.associateBy { it.getId() }

    /**
     * Locate a price provider for the requested market.
     *
     * @param market who is pricing this for this market
     * @return the provider that supports the asset
     */
    @Cacheable("provider")
    fun getMarketDataProvider(market: Market): MarketDataPriceProvider = resolveProvider(market)

    fun getMarketDataProvider(provider: String): MarketDataPriceProvider =
        providers[provider.uppercase(Locale.getDefault())]
            ?: throw BusinessException("Unknown market data provider: $provider")

    /**
     * The search surface of the provider that prices [market], so a market-scoped search asks the
     * provider it will be priced from; null when that provider cannot search.
     */
    fun getSearchProvider(market: Market): AssetSearchProvider? = searchProviders[resolveProvider(market).getId()]

    /**
     * Every search surface in provider precedence order. Used by asset-search fan-out so a
     * header-bar keyword with no market still reaches each one (e.g. EODHD when configured,
     * regardless of FIGI's coverage).
     */
    fun getSearchProviders(): List<AssetSearchProvider> = providers.keys.mapNotNull { searchProviders[it] }

    private fun resolveProvider(market: Market): MarketDataPriceProvider {
        // ToDo: Map Market to Provider
        for ((_, provider) in providers) {
            if (provider.isMarketSupported(market)) {
                return provider
            }
        }
        log.error(
            "Unable to identify a provider for {}",
            market
        )
        return providers.getValue(CashProviderService.ID)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MdFactory::class.java)
    }
}