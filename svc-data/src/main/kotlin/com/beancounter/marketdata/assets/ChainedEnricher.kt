package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.slf4j.LoggerFactory

/**
 * Enricher that tries multiple enrichers in order, merging results.
 * The first enricher to provide a name wins for identity fields (name, category).
 * Subsequent enrichers can still contribute the priceSymbol if the
 * earlier enricher only set it to the asset code (i.e. no real mapping).
 * Falls back to DefaultEnricher if no enricher provides a name.
 */
class ChainedEnricher(
    private val enrichers: List<AssetEnricher>,
    private val defaultEnricher: DefaultEnricher
) : AssetEnricher {
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput
    ): Asset {
        var best: Asset? = null
        for (enricher in enrichers) {
            try {
                val result = enricher.enrich(id, market, assetInput)
                if (best == null && !result.name.isNullOrBlank()) {
                    best = result
                    log.debug(
                        "{}/{} enriched by {}",
                        market.code,
                        assetInput.code,
                        enricher.id()
                    )
                } else if (best != null && hasBetterPriceSymbol(best, result)) {
                    best = best.copy(priceSymbol = result.priceSymbol)
                    log.debug(
                        "{}/{} priceSymbol set by {}",
                        market.code,
                        assetInput.code,
                        enricher.id()
                    )
                } else {
                    log.debug(
                        "{}/{} not resolved by {}, trying next",
                        market.code,
                        assetInput.code,
                        enricher.id()
                    )
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                log.warn(
                    "{}/{} failed in {}: {}",
                    market.code,
                    assetInput.code,
                    enricher.id(),
                    e.message
                )
            }
        }
        if (best != null) {
            return best
        }
        log.debug(
            "{}/{} not resolved by any enricher, using default",
            market.code,
            assetInput.code
        )
        return defaultEnricher.enrich(id, market, assetInput)
    }

    private fun hasBetterPriceSymbol(
        current: Asset,
        candidate: Asset
    ): Boolean =
        candidate.priceSymbol != null &&
            candidate.priceSymbol != candidate.code &&
            (current.priceSymbol == null || current.priceSymbol == current.code)

    override fun canEnrich(asset: Asset): Boolean = asset.name.isNullOrBlank()

    override fun id(): String = enrichers.joinToString(",") { it.id() }

    companion object {
        private val log = LoggerFactory.getLogger(ChainedEnricher::class.java)
    }
}