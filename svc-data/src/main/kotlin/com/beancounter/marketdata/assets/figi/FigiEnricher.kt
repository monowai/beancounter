package com.beancounter.marketdata.assets.figi

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetEnricher
import com.beancounter.marketdata.assets.DefaultEnricher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Enrich assets from bloomberg OpenFigi if supported.
 */
@Service
class FigiEnricher(val defaultEnricher: DefaultEnricher) : AssetEnricher {
    private lateinit var figiProxy: FigiProxy

    @Autowired(required = false)
    fun setFigiProxy(figiProxy: FigiProxy) {
        this.figiProxy = figiProxy
    }

    // @Cacheable(value = "asset.ext") //, unless = "#result == null"
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput,
    ): Asset {
        return figiProxy.find(market, assetInput.code, id = id) ?: defaultEnricher.enrich(
            id,
            market,
            assetInput,
        )
    }

    override fun canEnrich(asset: Asset): Boolean {
        return asset.name == null
    }

    override fun id(): String {
        return "FIGI"
    }
}
