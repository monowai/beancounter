package com.beancounter.marketdata.assets.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetEnricher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Enrich assets from bloomberg OpenFigi if supported.
 */
@Service
class FigiEnricher : AssetEnricher {
    private var figiProxy: FigiProxy? = null

    @Autowired(required = false)
    fun setFigiProxy(figiProxy: FigiProxy?) {
        this.figiProxy = figiProxy
    }

    // @Cacheable(value = "asset.ext") //, unless = "#result == null"
    override fun enrich(market: Market, code: String, defaultName: String?): Asset? {
        return figiProxy!!.find(market, code)
    }

    override fun canEnrich(asset: Asset): Boolean {
        return asset.name == null
    }
}
