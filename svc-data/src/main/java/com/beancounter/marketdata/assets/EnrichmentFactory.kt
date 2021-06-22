package com.beancounter.marketdata.assets

import com.beancounter.common.model.Market
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * The enricher provides missing Asset data properties such as name.
 */
@Service
class EnrichmentFactory {
    private lateinit var enrichers: MutableMap<String, AssetEnricher>

    @Value("\${beancounter.enricher:ALPHA}")
    lateinit var defEnricher: String

    @Autowired
    fun setEnrichers(figiEnricher: AssetEnricher, alphaEnricher: AssetEnricher) {
        enrichers = HashMap()
        enrichers[ALPHA] = alphaEnricher
        enrichers[FIGI] = figiEnricher
        enrichers[MOCK] = MockEnricher()
        log.info("Registered {} Asset Enrichers.  Default: {}", enrichers.keys.size, defEnricher)
    }

    fun getEnricher(market: Market): AssetEnricher {
        var enricher = market.enricher
        if (enricher == null) {
            enricher = defEnricher
        }

        return enrichers[enricher.uppercase(Locale.getDefault())]!!
    }

    companion object {
        const val ALPHA = "ALPHA"
        const val MOCK = "MOCK"
        const val FIGI = "FIGI"
        private val log = LoggerFactory.getLogger(EnrichmentFactory::class.java)
    }
}
