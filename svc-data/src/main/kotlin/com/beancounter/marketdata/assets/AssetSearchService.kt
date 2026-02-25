package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiFilterRequest
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.assets.figi.FigiSearch
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.providers.marketstack.MarketStackConfig
import com.beancounter.marketdata.providers.marketstack.MarketStackGateway
import com.beancounter.marketdata.registration.SystemUserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Service for searching assets by keyword.
 * Supports searching private/custom assets and public assets via AlphaVantage or MarketStack.
 */
@Service
class AssetSearchService(
    private val assetRepository: AssetRepository,
    private val alphaProxy: AlphaProxy,
    private val alphaConfig: AlphaConfig,
    private val marketStackGateway: MarketStackGateway,
    private val marketStackConfig: MarketStackConfig,
    private val figiGateway: FigiGateway,
    private val figiConfig: FigiConfig,
    private val marketService: MarketService,
    private val systemUserService: SystemUserService
) {
    private val log = LoggerFactory.getLogger(AssetSearchService::class.java)

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
     * Search for assets by keyword.
     * @param keyword The search term (asset code, name, or partial match)
     * @param market Optional market code.
     *               - "PRIVATE": searches user's custom assets only
     *               - "FIGI": searches FIGI globally (expand/force external search)
     *               - null/blank: searches local database only (code + name matching)
     *               - Other values: searches local database (filtered by market) + external provider, merged
     */
    fun search(
        keyword: String,
        market: String?
    ): AssetSearchResponse {
        if (keyword.isBlank() || keyword.length < 2) {
            return AssetSearchResponse(emptyList())
        }

        return when {
            market.equals("PRIVATE", ignoreCase = true) -> searchPrivateAssets(keyword)
            market.equals("FIGI", ignoreCase = true) -> searchFigiGlobal(keyword)
            market.isNullOrBlank() -> searchLocalAssets(keyword)
            else -> searchMarketAssets(keyword, market)
        }
    }

    /**
     * Search for assets in a specific market.
     * Combines local database results (code + name matching) with external provider results,
     * deduplicating by symbol.
     */
    private fun searchMarketAssets(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        val localResults = searchLocalAssets(keyword)
        val filtered =
            localResults.data.filter {
                it.market.equals(market, ignoreCase = true)
            }

        val externalResults = searchPublicAssets(keyword, market)
        val localSymbols = filtered.map { it.symbol.uppercase() }.toSet()
        val newFromExternal =
            externalResults.data.filter {
                !localSymbols.contains(it.symbol.uppercase())
            }

        return AssetSearchResponse(filtered + newFromExternal)
    }

    private fun searchPrivateAssets(keyword: String): AssetSearchResponse {
        val user = systemUserService.getActiveUser()
        if (user == null) {
            log.warn("No active user found for private asset search")
            return AssetSearchResponse(emptyList())
        }

        val assets = assetRepository.searchByUserAndCodeOrName(user.id, keyword)
        val results = assets.map { toSearchResult(it) }
        return AssetSearchResponse(results)
    }

    /**
     * Search all assets in the local database by code or name.
     * Falls back to FIGI global search when local results are empty.
     * De-duplicates by code, preferring US market when duplicates exist.
     */
    private fun searchLocalAssets(keyword: String): AssetSearchResponse {
        val assets = assetRepository.searchByCodeOrName(keyword)
        // Group by code and pick the preferred market variant (US preferred)
        val marketPriority = listOf("US", "NYSE", "NASDAQ", "ASX", "NZX")
        val deduped =
            assets
                .groupBy { it.code.uppercase() }
                .map { (_, variants) ->
                    variants.minByOrNull { asset ->
                        val idx = marketPriority.indexOf(asset.marketCode)
                        if (idx >= 0) idx else marketPriority.size
                    } ?: variants.first()
                }
        val results = deduped.take(50).map { toLocalSearchResult(it) }

        // Fall back to FIGI global search when local results are empty
        if (results.isEmpty() && figiConfig.enabled) {
            log.debug("No local results for '{}', falling back to FIGI global search", keyword)
            return searchFigiGlobal(keyword)
        }

        return AssetSearchResponse(results)
    }

    // Markets that use FIGI for search (configured via figi.search.markets)
    private val figiSearchMarkets: Set<String> by lazy {
        figiConfig.getSearchMarkets()
    }

    private fun searchPublicAssets(
        keyword: String,
        market: String?
    ): AssetSearchResponse {
        // Use FIGI for markets configured in figi.search.markets
        if (market != null && figiSearchMarkets.contains(market.uppercase())) {
            return searchFigiAssets(keyword, market)
        }

        // Use MarketStack for markets configured in mstack.markets (but not FIGI)
        if (market != null && marketStackMarkets.contains(market.uppercase())) {
            return searchMarketStackAssets(keyword, market)
        }

        // Default to AlphaVantage for other markets
        return searchAlphaVantageAssets(keyword, market)
    }

    private fun searchFigiAssets(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        return try {
            val resolvedMarket = marketService.getMarket(market)
            val exchCode =
                FigiConfig.getExchCode(market.uppercase())
                    ?: error("No FIGI exchange code for market $market")

            // First try exact ticker lookup via /v3/mapping
            val tickerResults = searchFigiByTicker(keyword, exchCode, resolvedMarket, market)
            if (tickerResults.isNotEmpty()) {
                log.debug("FIGI ticker lookup for '{}' found {} results", keyword, tickerResults.size)
                return AssetSearchResponse(tickerResults)
            }

            // Fall back to filter search for name-based matching
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
                            type = result.securityType2 ?: "Equity",
                            region = market,
                            currency = resolvedMarket.currency.code,
                            market = market
                        )
                    }

            log.debug("FIGI filter search for '{}' returning {} filtered results", keyword, results.size)
            AssetSearchResponse(results)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching FIGI for assets: ${e.message}", e)
            // Fall back to MarketStack if available
            if (marketStackMarkets.contains(market.uppercase())) {
                return searchMarketStackAssets(keyword, market)
            }
            AssetSearchResponse(emptyList())
        }
    }

    /**
     * Search FIGI globally without exchange restriction.
     * Maps FIGI exchange codes back to BC market codes.
     */
    private fun searchFigiGlobal(keyword: String): AssetSearchResponse =
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
                    }.map { result ->
                        val market = result.exchCode?.let { FigiConfig.getMarketCode(it) }
                        AssetSearchResult(
                            symbol = result.ticker ?: keyword,
                            name = result.name ?: "",
                            type = result.securityType2 ?: "Equity",
                            region = market ?: result.exchCode,
                            currency = null,
                            market = market ?: result.exchCode
                        )
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

            if (response?.error != null || response?.data.isNullOrEmpty()) {
                emptyList()
            } else {
                response.data!!
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

    companion object {
        private val ALLOWED_SECURITY_TYPES =
            setOf(
                "COMMON STOCK",
                "REIT",
                "DEPOSITARY RECEIPT",
                "MUTUAL FUND"
            )
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
                    // Extract the asset code from symbol (e.g., "D05.SI" -> "D05")
                    val assetCode = ticker.symbol.substringBefore(".")
                    AssetSearchResult(
                        symbol = assetCode,
                        name = ticker.name,
                        type = "Equity",
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

    private fun toSearchResult(asset: Asset): AssetSearchResult {
        // Private asset codes are stored as "{userId}.{CODE}" - extract just the code portion
        val displayCode = asset.code.substringAfter(".")
        return AssetSearchResult(
            symbol = displayCode,
            name = asset.name ?: displayCode,
            type = asset.category,
            region = "PRIVATE",
            currency = asset.accountingType?.currency?.code,
            market = "PRIVATE",
            assetId = asset.id
        )
    }

    private fun toLocalSearchResult(asset: Asset): AssetSearchResult {
        // For private assets, display code without userId prefix
        val displayCode =
            if (asset.marketCode == "PRIVATE") {
                asset.code.substringAfter(".")
            } else {
                asset.code
            }
        // market is @Transient so resolve currency from MarketService
        val currency =
            asset.accountingType?.currency?.code
                ?: try {
                    marketService.getMarket(asset.marketCode).currency.code
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception
                ) {
                    log.warn("Could not resolve currency for market ${asset.marketCode}: ${e.message}")
                    null
                }
        return AssetSearchResult(
            symbol = displayCode,
            name = asset.name ?: displayCode,
            type = asset.category,
            region = asset.marketCode,
            currency = currency,
            market = asset.marketCode,
            assetId = asset.id
        )
    }
}