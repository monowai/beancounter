package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AccountingTypeService
import com.beancounter.marketdata.assets.AssetEnricher
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.currency.CurrencyService
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
    private val defaultEnricher: DefaultEnricher,
    private val accountingTypeService: AccountingTypeService,
    private val currencyService: CurrencyService
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

            val ticker = pickTicker(response.data?.tickers ?: emptyList(), assetInput.code)

            if (ticker != null) {
                val marketAlias = market.getAlias(MarketStackService.ID)
                val priceSymbol =
                    if (!marketAlias.isNullOrEmpty()) {
                        val baseCode = ticker.symbol.substringBefore(".")
                        "$baseCode.$marketAlias"
                    } else {
                        ticker.symbol
                    }
                val currency = currencyService.getCode(market.currencyId)
                val accountingType =
                    accountingTypeService.getOrCreate(
                        category = "Equity",
                        currency = currency
                    )
                Asset(
                    code = assetInput.code.uppercase(Locale.getDefault()),
                    id = id,
                    name = ticker.name,
                    market = market,
                    marketCode = market.code,
                    priceSymbol = priceSymbol,
                    category = "Equity",
                    accountingType = accountingType
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

    /**
     * Pick the best-matching ticker from a MarketStack response.
     *
     * Priority:
     * 1. Exact symbol match — the base code (before ".") equals the search term exactly.
     *    Prevents "KEP" from fuzzy-matching Keppel REIT (K71U) instead of Keppel Corp (BN4).
     * 2. Single unambiguous name/symbol contains match — accepted only when there is exactly
     *    one candidate. Multiple matches (e.g. "KEP" hitting both K71U and BN4 via "Keppel")
     *    are rejected as ambiguous; the caller then falls back to the market-alias code path.
     */
    internal fun pickTicker(
        tickers: List<com.beancounter.marketdata.providers.marketstack.model.MarketStackTicker>,
        code: String
    ): com.beancounter.marketdata.providers.marketstack.model.MarketStackTicker? {
        val exact =
            tickers.firstOrNull { t ->
                t.symbol.substringBefore(".").equals(code, ignoreCase = true)
            }
        if (exact != null) return exact

        val fuzzy =
            tickers.filter { t ->
                t.name.contains(code, ignoreCase = true) ||
                    t.symbol.contains(code, ignoreCase = true)
            }
        return if (fuzzy.size == 1) fuzzy.first() else null
    }

    override fun canEnrich(asset: Asset): Boolean =
        asset.name.isNullOrBlank() && marketStackMicCodes.containsKey(asset.market.code.uppercase())

    override fun id(): String = ID

    companion object {
        const val ID = "MSTACK"
    }
}