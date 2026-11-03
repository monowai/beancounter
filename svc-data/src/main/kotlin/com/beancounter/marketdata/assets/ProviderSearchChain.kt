package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.providers.marketstack.MarketStackConfig
import com.beancounter.marketdata.providers.marketstack.MarketStackGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * The search fallback when a market's price provider has no search of its own:
 * FIGI, MarketStack or AlphaVantage, chosen by market configuration.
 */
@Service
class ProviderSearchChain(
    private val alphaProxy: AlphaProxy,
    private val alphaConfig: AlphaConfig,
    private val marketStackGateway: MarketStackGateway,
    private val marketStackConfig: MarketStackConfig,
    private val figiAssetSearch: FigiAssetSearch,
    private val marketService: MarketService
) {
    private val log = LoggerFactory.getLogger(ProviderSearchChain::class.java)

    @Value($$"${beancounter.market.providers.alpha.key:demo}")
    private val alphaApiKey: String = "demo"

    // Markets that use MarketStack for search (configured via mstack.markets)
    private val marketStackMarkets: Set<String> by lazy {
        marketStackConfig.markets
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Search [market] through the provider chain: FIGI where configured (AlphaVantage when FIGI
     * finds nothing), MarketStack where configured, AlphaVantage otherwise.
     */
    fun search(
        keyword: String,
        market: String?
    ): AssetSearchResponse =
        when {
            // Use FIGI for markets configured in figi.search.markets.
            // FIGI's filter endpoint returns exact-ticker hits and full-name
            // matches but does poorly on short ticker prefixes (e.g. "COW")
            // because it surfaces option chains that we filter out. Fall back
            // to AlphaVantage SYMBOL_SEARCH when FIGI yields nothing so users
            // get fuzzy ticker/name matches as they type.
            market != null && figiAssetSearch.searchMarkets.contains(market.uppercase()) -> {
                val figiResults = figiOrFallback(keyword, market)
                if (figiResults.data.isNotEmpty()) figiResults else searchAlphaVantageAssets(keyword, market)
            }
            // Use MarketStack for markets configured in mstack.markets (but not FIGI)
            market != null && marketStackMarkets.contains(market.uppercase()) -> {
                searchMarketStackAssets(keyword, market)
            }
            // Default to AlphaVantage for other markets
            else -> {
                searchAlphaVantageAssets(keyword, market)
            }
        }

    // FIGI on one market; when FIGI cannot be asked, MarketStack if it serves the market.
    private fun figiOrFallback(
        keyword: String,
        market: String
    ): AssetSearchResponse =
        try {
            figiAssetSearch.inMarket(keyword, market)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching FIGI for assets: ${e.message}", e)
            if (marketStackMarkets.contains(market.uppercase())) {
                searchMarketStackAssets(keyword, market)
            } else {
                AssetSearchResponse(emptyList())
            }
        }

    private fun searchMarketStackAssets(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        return try {
            val resolvedMarket = marketService.getMarket(market)
            val exchangeMic =
                MarketStackConfig.getMicCode(market)
                    ?: error("No MarketStack MIC code for market $market")

            val response =
                marketStackGateway.searchTickers(
                    exchangeMic = exchangeMic,
                    searchTerm = keyword,
                    apiKey = marketStackConfig.apiKey
                )

            if (response.error != null) {
                log.warn("MarketStack search error: ${response.error}")
                return AssetSearchResponse(emptyList())
            }

            val results =
                response.data?.tickers?.map { ticker ->
                    // Strip the exchange suffix only — `substringBeforeLast` keeps any class
                    // indicator inside the symbol (e.g. "BRK.B.US" → "BRK.B", "D05.SI" → "D05").
                    val assetCode = ticker.symbol.substringBeforeLast(".")
                    AssetSearchResult(
                        symbol = assetCode,
                        name = ticker.name,
                        type = EQUITY,
                        region = market,
                        currency = resolvedMarket.currency.code,
                        market = market
                    )
                } ?: emptyList()

            AssetSearchResponse(results)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching MarketStack for assets: ${e.message}", e)
            AssetSearchResponse(emptyList())
        }
    }

    private fun searchAlphaVantageAssets(
        keyword: String,
        market: String?
    ): AssetSearchResponse =
        try {
            val searchSymbol =
                if (market != null && !alphaConfig.isNullMarket(market)) {
                    "${alphaConfig.translateSymbol(keyword)}.$market"
                } else {
                    alphaConfig.translateSymbol(keyword)
                }

            val result = alphaProxy.search(searchSymbol, alphaApiKey)
            val response =
                alphaConfig.getObjectMapper().readValue(
                    result,
                    AssetSearchResponse::class.java
                )

            // Add market info to results
            AssetSearchResponse(
                response.data.map { it.copy(market = market ?: it.region) }
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Return empty results on any search failure
            log.error("Error searching AlphaVantage for assets: ${e.message}", e)
            AssetSearchResponse(emptyList())
        }

    internal companion object {
        /** Search-result type when a provider does not name one. */
        const val EQUITY = "Equity"
    }
}