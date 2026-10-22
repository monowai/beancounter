package com.beancounter.marketdata.assets.figi

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetEnricher
import com.beancounter.marketdata.assets.DefaultEnricher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Enrich assets from bloomberg OpenFigi if supported.
 */
@Service
class FigiEnricher(
    val defaultEnricher: DefaultEnricher
) : AssetEnricher {
    private lateinit var figiProxy: FigiProxy

    @Autowired(required = false)
    fun setFigiProxy(figiProxy: FigiProxy) {
        this.figiProxy = figiProxy
    }

    // @Cacheable(value = "asset.ext") //, unless = "#result == null"
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput
    ): Asset {
        val found =
            try {
                figiProxy.find(
                    market,
                    assetInput.code,
                    id = id
                )
            } catch (e: FigiProviderException) {
                // Persist a default-named asset rather than fail the create.
                log.warn("FIGI error, using default enricher: {}", e.message)
                null
            }
        return found ?: defaultEnricher.enrich(
            id,
            market,
            assetInput
        )
    }

    override fun canEnrich(asset: Asset): Boolean = asset.name.isNullOrBlank()

    override fun id(): String = "FIGI"

    companion object {
        private val log = LoggerFactory.getLogger(FigiEnricher::class.java)
    }
}