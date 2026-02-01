package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiFilterRequest
import com.beancounter.marketdata.assets.figi.FigiGateway
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

    // MarketStack V2 exchange MIC codes for the tickers search endpoint
    // The mstack alias stores the V2 price suffix (e.g., "SI"), but the
    // exchange tickers endpoint needs the MIC code (e.g., "XSES")
    private val marketStackMicCodes =
        mapOf(
            "SGX" to "XSES",
            "NZX" to "XNZE"
        )

    /**
     * Search for assets by keyword.
     * @param keyword The search term (asset code, name, or partial match)
     * @param market Optional market code.
     *               - "PRIVATE": searches user's custom assets only
     *               - "LOCAL": searches all assets in the database (no external API calls)
     *               - Other values: uses AlphaVantage SYMBOL_SEARCH
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
            market.equals("LOCAL", ignoreCase = true) -> searchLocalAssets(keyword)
            market.equals("FIGI", ignoreCase = true) -> searchFigiGlobal(keyword)
            market != null -> searchPublicAssets(keyword, market)
            else -> searchPublicAssets(keyword, null)
        }
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
     * Does not call external providers - only searches existing assets.
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
                    ?: throw IllegalStateException("No FIGI exchange code for market $market")

            val response =
                figiGateway.filter(
                    FigiFilterRequest(
                        query = keyword,
                        exchCode = exchCode
                    ),
                    figiConfig.apiKey
                )

            val results =
                response.data
                    .filter { result ->
                        val type = result.securityType2?.uppercase()
                        type != null && ALLOWED_SECURITY_TYPES.contains(type)
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
                marketStackMicCodes[market.uppercase()]
                    ?: throw IllegalStateException("No MarketStack MIC code for market $market")

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
            currency = asset.priceSymbol,
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
        return AssetSearchResult(
            symbol = displayCode,
            name = asset.name ?: displayCode,
            type = asset.category,
            region = asset.marketCode,
            currency = asset.priceSymbol ?: asset.market.currency.code,
            market = asset.marketCode,
            assetId = asset.id
        )
    }
}