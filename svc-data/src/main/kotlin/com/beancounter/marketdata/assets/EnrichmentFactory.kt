package com.beancounter.marketdata.assets

import com.beancounter.common.model.Market
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Return an enricher that can append missing Asset data properties such as name.
 */
@Service
@Transactional
class EnrichmentFactory(val defaultEnricher: DefaultEnricher) {
    private var enrichers: MutableMap<String, AssetEnricher> = HashMap()

    @Value("\${beancounter.enricher:ALPHA}")
    lateinit var defEnricher: String

    @Autowired
    fun setEnrichers(figiEnricher: AssetEnricher, alphaEnricher: AssetEnricher) {
        register(defaultEnricher)
        register(alphaEnricher)
        register(figiEnricher)
        log.info("Registered ${enrichers.keys.size} Asset Enrichers.  Default: $defEnricher")
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

    companion object {
        private val log = LoggerFactory.getLogger(EnrichmentFactory::class.java)
    }
}
