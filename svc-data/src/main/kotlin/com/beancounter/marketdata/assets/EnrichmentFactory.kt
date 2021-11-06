package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Locale
import javax.transaction.Transactional

/**
 * Return an enricher that can append missing Asset data properties such as name.
 */
@Service
@Transactional
class EnrichmentFactory(private val assetRepository: AssetRepository) {
    private var enrichers: MutableMap<String, AssetEnricher> = HashMap()

    @Value("\${beancounter.enricher:ALPHA}")
    lateinit var defEnricher: String

    @Autowired
    fun setEnrichers(figiEnricher: AssetEnricher, alphaEnricher: AssetEnricher, echoEnricher: EchoEnricher) {
        register(alphaEnricher)
        register(figiEnricher)
        register(echoEnricher)
        log.info("Registered {} Asset Enrichers.  Default: {}", enrichers.keys.size, defEnricher)
    }

    fun register(enricher: AssetEnricher) {
        enrichers[enricher.id().uppercase()] = enricher
    }

    fun getEnricher(market: Market): AssetEnricher {
        var enricher = market.enricher
        if (enricher == null) {
            enricher = defEnricher
        }

        return enrichers[enricher.uppercase(Locale.getDefault())]!!
    }

    fun enrich(asset: Asset): Asset {
        val enricher = getEnricher(asset.market)
        if (enricher.canEnrich(asset)) {
            val enriched = enricher.enrich(asset.market, asset.code, asset.name)
            if (enriched != null) {
                enriched.id = asset.id
                assetRepository.save(enriched) // Hmm, not sure the Repo should be here
                return enriched
            }
        }
        return asset
    }

    companion object {
        private val log = LoggerFactory.getLogger(EnrichmentFactory::class.java)
    }
}
