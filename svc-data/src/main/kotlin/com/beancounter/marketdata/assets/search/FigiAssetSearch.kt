package com.beancounter.marketdata.assets.search

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiFilterRequest
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.assets.figi.FigiSearch
import com.beancounter.marketdata.markets.MarketService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Asset search against OpenFIGI: on one market, or globally with FIGI exchange codes mapped
 * back to Beancounter markets. Only listed equity-like security types are returned.
 */
@Service
class FigiAssetSearch(
    private val figiGateway: FigiGateway,
    private val figiConfig: FigiConfig,
    private val marketService: MarketService
) {
    private val log = LoggerFactory.getLogger(FigiAssetSearch::class.java)

    /** Whether FIGI search is switched on. */
    val enabled: Boolean get() = figiConfig.enabled

    // Markets that use FIGI for search (configured via figi.search.markets)
    val searchMarkets: Set<String> by lazy {
        figiConfig.getSearchMarkets()
    }

    /**
     * FIGI search on one market: an exact ticker lookup first, then a name match. Throws when
     * FIGI cannot be asked; the caller chooses the fallback.
     */
    fun inMarket(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        val resolvedMarket = marketService.getMarket(market)
        val exchCode =
            FigiConfig.getExchCode(market.uppercase())
                ?: error("No FIGI exchange code for market $market")

        // First try exact ticker lookup via /v3/mapping
        val tickerResults = searchFigiByTicker(keyword, exchCode, resolvedMarket, market)
        return if (tickerResults.isNotEmpty()) {
            log.debug("FIGI ticker lookup for '{}' found {} results", keyword, tickerResults.size)
            AssetSearchResponse(tickerResults)
        } else {
            // Fall back to filter search for name-based matching
            searchFigiByName(keyword, exchCode, resolvedMarket, market)
        }
    }

    private fun searchFigiByName(
        keyword: String,
        exchCode: String,
        resolvedMarket: Market,
        market: String
    ): AssetSearchResponse {
        val response =
            figiGateway.filter(
                FigiFilterRequest(
                    query = keyword,
                    exchCode = exchCode
                ),
                figiConfig.apiKey
            )

        log.debug(
            "FIGI filter search for '{}' on {} returned {} results",
            keyword,
            market,
            response.data.size
        )

        val results =
            response.data
                .filter { result ->
                    val type = result.securityType2?.uppercase()
                    val allowed = type != null && ALLOWED_SECURITY_TYPES.contains(type)
                    if (!allowed && result.ticker != null) {
                        log.debug(
                            "Filtered out {} ({}) - securityType2: {}",
                            result.ticker,
                            result.name,
                            result.securityType2
                        )
                    }
                    allowed
                }.map { result ->
                    AssetSearchResult(
                        symbol = result.ticker ?: keyword,
                        name = result.name ?: "",
                        type = result.securityType2 ?: ProviderSearchChain.EQUITY,
                        region = market,
                        currency = resolvedMarket.currency.code,
                        market = market
                    )
                }

        log.debug("FIGI filter search for '{}' returning {} filtered results", keyword, results.size)
        return AssetSearchResponse(results)
    }

    /**
     * Search FIGI globally without exchange restriction.
     * Maps FIGI exchange codes back to BC market codes.
     */
    fun global(keyword: String): AssetSearchResponse =
        try {
            val response =
                figiGateway.filter(
                    FigiFilterRequest(query = keyword),
                    figiConfig.apiKey
                )

            val results =
                response.data
                    .filter { result ->
                        val type = result.securityType2?.uppercase()
                        type != null && ALLOWED_SECURITY_TYPES.contains(type)
                    }.mapNotNull { result ->
                        // Drop results whose FIGI exchange code does not map to a BC
                        // market — the UI would otherwise post the raw FIGI code to
                        // /api/assets and trigger a 404 ("Unable to resolve market
                        // code"). Matches the searchFigiByTicker path which also
                        // requires a configured market.
                        result.exchCode
                            ?.let(FigiConfig::getMarketCode)
                            ?.let { market ->
                                AssetSearchResult(
                                    symbol = result.ticker ?: keyword,
                                    name = result.name ?: "",
                                    type = result.securityType2 ?: ProviderSearchChain.EQUITY,
                                    region = market,
                                    currency = null,
                                    market = market
                                )
                            }
                    }

            AssetSearchResponse(results)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching FIGI globally: ${e.message}", e)
            AssetSearchResponse(emptyList())
        }

    /**
     * Try exact ticker lookup via FIGI /v3/mapping endpoint.
     * Returns empty list if no match found.
     */
    private fun searchFigiByTicker(
        ticker: String,
        exchCode: String,
        resolvedMarket: Market,
        marketCode: String
    ): List<AssetSearchResult> =
        try {
            val searchRequest =
                FigiSearch(
                    idValue = ticker.uppercase(),
                    exchCode = exchCode
                )
            val responses = figiGateway.search(listOf(searchRequest), figiConfig.apiKey)
            val response = responses.firstOrNull()

            val data = response?.data
            if (response?.error != null || data.isNullOrEmpty()) {
                emptyList()
            } else {
                data
                    .filter { asset ->
                        val type = asset.securityType2.uppercase()
                        ALLOWED_SECURITY_TYPES.contains(type)
                    }.map { asset ->
                        AssetSearchResult(
                            symbol = asset.ticker,
                            name = asset.name,
                            type = asset.securityType2,
                            region = marketCode,
                            currency = resolvedMarket.currency.code,
                            market = marketCode
                        )
                    }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("FIGI ticker lookup failed for '{}': {}", ticker, e.message)
            emptyList()
        }

    private companion object {
        val ALLOWED_SECURITY_TYPES =
            setOf(
                "COMMON STOCK",
                "REIT",
                "DEPOSITARY RECEIPT",
                "MUTUAL FUND"
            )
    }
}