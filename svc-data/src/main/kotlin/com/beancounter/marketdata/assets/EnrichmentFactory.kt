package com.beancounter.marketdata.assets

import com.beancounter.common.model.Market
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Return an enricher that can append missing Asset data properties such as name.
 */
@Service
class EnrichmentFactory(
    val defaultEnricher: DefaultEnricher
) {
    private var enrichers: MutableMap<String, AssetEnricher> = HashMap()

    @Value("\${beancounter.enricher:ALPHA}")
    lateinit var defEnricher: String

    @Autowired
    fun setEnrichers(
        figiEnricher: AssetEnricher,
        alphaEnricher: AssetEnricher,
        privateMarketEnricher: PrivateMarketEnricher,
        marketStackEnricher: AssetEnricher
    ) {
        register(defaultEnricher)
        register(alphaEnricher)
        register(figiEnricher)
        register(privateMarketEnricher)
        register(marketStackEnricher)
        log.info("Registered ${enrichers.keys.size} Asset Enrichers.  Default: $defEnricher")
    }

    fun register(enricher: AssetEnricher) {
        enrichers[enricher.id().uppercase()] = enricher
    }

    fun getEnricher(market: Market): AssetEnricher {
        val enricherConfig = (market.enricher ?: defEnricher).uppercase(Locale.getDefault())
        val parts = enricherConfig.split(",").map { it.trim() }
        if (parts.size == 1) {
            return enrichers[parts[0]]!!
        }
        val chain = parts.mapNotNull { enrichers[it] }
        return ChainedEnricher(chain, defaultEnricher)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EnrichmentFactory::class.java)
    }
}