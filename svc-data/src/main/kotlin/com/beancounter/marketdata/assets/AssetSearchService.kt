package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Asset
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.registration.SystemUserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Service for searching assets by keyword.
 * Supports searching private/custom assets and public assets via AlphaVantage.
 */
@Service
class AssetSearchService(
    private val assetRepository: AssetRepository,
    private val alphaProxy: AlphaProxy,
    private val alphaConfig: AlphaConfig,
    private val systemUserService: SystemUserService
) {
    private val log = LoggerFactory.getLogger(AssetSearchService::class.java)

    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private val apiKey: String = "demo"

    /**
     * Search for assets by keyword.
     * @param keyword The search term (asset code or partial code)
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

        val assets = assetRepository.searchByUserAndCode(user.id, keyword)
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

    private fun searchPublicAssets(
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

            val result = alphaProxy.search(searchSymbol, apiKey)
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
            log.error("Error searching for assets: ${e.message}", e)
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
            currency = asset.priceSymbol ?: asset.market.currency?.code,
            market = asset.marketCode,
            assetId = asset.id
        )
    }
}