package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market

interface AssetEnricher {
    fun enrich(market: Market, code: String, defaultName: String?): Asset?
    fun canEnrich(asset: Asset): Boolean
}