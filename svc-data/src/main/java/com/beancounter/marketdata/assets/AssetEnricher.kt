package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market

/**
 * Enricher take a public facing market and asset code and return an Asset with
 * whatever information it can fill, filled.  Normally this is name.
 */
interface AssetEnricher {
    /**
     * Return enriched Asset props for this market/code, setting the name as default if necessary
     */
    fun enrich(market: Market, code: String, defaultName: String?): Asset?

    /**
     * Can this enricher enrich this asset?
     */
    fun canEnrich(asset: Asset): Boolean
}
