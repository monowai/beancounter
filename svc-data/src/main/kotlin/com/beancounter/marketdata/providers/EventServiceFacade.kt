package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdEventService
import org.springframework.stereotype.Service

/**
 * Routes corporate-event lookups to the provider that owns the asset's market.
 *
 * Today there are only two event providers (AlphaVantage and EODHD), so the dispatch is a simple
 * caller-side check rather than a generalised interface + factory. If a third provider lands, lift
 * this into a `MarketDataEventProvider` interface registered in [MdFactory] — the rule-of-three
 * threshold for justified abstraction.
 *
 * Routing rule: if `asset.market.code` is in the EODHD `markets` allowlist, EODHD answers; otherwise
 * AlphaVantage answers (which itself falls back to repo-cached events for unsupported markets).
 * With EODHD's default empty allowlist, every market falls through to AlphaVantage — preserving the
 * pre-EODHD behaviour exactly.
 */
@Service
class EventServiceFacade(
    private val assetFinder: AssetFinder,
    private val eodhdEventService: EodhdEventService,
    private val alphaEventService: AlphaEventService,
    private val eodhdConfig: EodhdConfig
) {
    fun getEvents(assetId: String): PriceResponse {
        val asset = assetFinder.find(assetId)
        return if (isEodhdMarket(asset.market.code)) {
            eodhdEventService.getEvents(asset)
        } else {
            alphaEventService.getEvents(asset)
        }
    }

    private fun isEodhdMarket(marketCode: String): Boolean {
        val supported = eodhdConfig.markets
        return !supported.isNullOrBlank() && supported.contains(marketCode)
    }
}