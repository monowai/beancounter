package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetEnricher
import com.beancounter.marketdata.assets.DefaultEnricher
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Enricher that looks up MarketStack symbol for assets.
 * Used when FIGI returns a user-friendly ticker (e.g., "DBS")
 * but we need the MarketStack symbol (e.g., "D05.SI") for pricing.
 */
@Service
class MarketStackEnricher(
    private val marketStackGateway: MarketStackGateway,
    private val marketStackConfig: MarketStackConfig,
    private val defaultEnricher: DefaultEnricher
) : AssetEnricher {
    private val log = LoggerFactory.getLogger(MarketStackEnricher::class.java)

    // MarketStack MIC codes for the tickers search endpoint
    private val marketStackMicCodes =
        mapOf(
            "SGX" to "XSES",
            "NZX" to "XNZE"
        )

    @Cacheable("asset.mstack.search")
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput
    ): Asset {
        val micCode = marketStackMicCodes[market.code.uppercase()]
        if (micCode == null) {
            log.debug("No MarketStack MIC code for market ${market.code}, using default enricher")
            return defaultEnricher.enrich(id, market, assetInput)
        }

        return try {
            val response =
                marketStackGateway.searchTickers(
                    exchangeMic = micCode,
                    searchTerm = assetInput.code,
                    apiKey = marketStackConfig.apiKey
                )

            val ticker =
                response.data?.tickers?.firstOrNull {
                    // Match by name containing the search term (case-insensitive)
                    it.name.contains(assetInput.code, ignoreCase = true) ||
                        it.symbol.contains(assetInput.code, ignoreCase = true)
                }

            if (ticker != null) {
                val marketAlias = market.getAlias(MarketStackService.ID)
                val priceSymbol =
                    if (!marketAlias.isNullOrEmpty()) {
                        // Extract base code and append market alias
                        val baseCode = ticker.symbol.substringBefore(".")
                        "$baseCode.$marketAlias"
                    } else {
                        ticker.symbol
                    }

                Asset(
                    code = assetInput.code.uppercase(Locale.getDefault()),
                    id = id,
                    name = ticker.name,
                    market = market,
                    marketCode = market.code,
                    priceSymbol = priceSymbol,
                    category = "Equity"
                )
            } else {
                log.debug("No MarketStack ticker found for ${assetInput.code} on ${market.code}")
                defaultEnricher.enrich(id, market, assetInput)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Error enriching via MarketStack: ${e.message}")
            defaultEnricher.enrich(id, market, assetInput)
        }
    }

    override fun canEnrich(asset: Asset): Boolean =
        asset.name == null && marketStackMicCodes.containsKey(asset.market.code.uppercase())

    override fun id(): String = ID

    companion object {
        const val ID = "MSTACK"
    }
}