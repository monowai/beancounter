package com.beancounter.marketdata.classification.enricher

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.marketdata.classification.ClassificationService
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaEtfProfileResponse
import com.beancounter.marketdata.providers.alpha.AlphaOverviewResponse
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Enriches assets with classification data from AlphaVantage.
 *
 * - Equities: Uses OVERVIEW endpoint to get Sector and Industry
 * - ETFs: Uses ETF_PROFILE endpoint to get sector weights
 */
@Service
class AlphaClassificationEnricher(
    private val alphaConfig: AlphaConfig,
    private val alphaProxy: AlphaProxy,
    private val classificationService: ClassificationService,
    private val etfLookThrough: AlphaEtfLookThrough
) : ClassificationEnricher {
    private val log = LoggerFactory.getLogger(AlphaClassificationEnricher::class.java)
    private val objectMapper = alphaConfig.getObjectMapper()

    @Value($$"${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    /**
     * Check if this asset can be enriched with classification data.
     */
    override fun canEnrich(asset: Asset): Boolean {
        val category = asset.category.uppercase()
        return category in EQUITY_CATEGORIES || category in ETF_CATEGORIES
    }

    /**
     * Check if this asset is an ETF.
     */
    override fun isEtf(asset: Asset): Boolean = asset.category.uppercase() in ETF_CATEGORIES

    /**
     * Check if this asset is an Equity.
     */
    override fun isEquity(asset: Asset): Boolean = asset.category.uppercase() in EQUITY_CATEGORIES

    /**
     * Enrich an asset with classification data. See [EnrichmentResult].
     */
    override fun enrichClassification(asset: Asset): EnrichmentResult =
        try {
            when {
                isEquity(asset) -> enrichEquity(asset)
                isEtf(asset) -> enrichEtf(asset)
                else -> EnrichmentResult.NO_DATA
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Gracefully handle API/parsing failures without propagating
            log.warn("Failed to enrich classification for ${asset.code}: ${e.message}")
            EnrichmentResult.FAILED
        }

    private fun enrichEquity(asset: Asset): EnrichmentResult {
        val symbol = alphaConfig.getPriceCode(asset)
        val response = alphaProxy.getOverview(symbol, apiKey)
        return when (AlphaResponseBody.of(response, objectMapper)) {
            AlphaResponseBody.RATE_LIMITED -> {
                rateLimited(symbol)
            }
            AlphaResponseBody.ERROR -> {
                logNoData(asset, symbol, "OVERVIEW returned an error or empty body")
                EnrichmentResult.FAILED
            }
            AlphaResponseBody.PAYLOAD -> {
                classifyOverview(
                    asset,
                    symbol,
                    objectMapper.readValue(response, AlphaOverviewResponse::class.java)
                )
            }
        }
    }

    private fun classifyOverview(
        asset: Asset,
        symbol: String,
        overview: AlphaOverviewResponse
    ): EnrichmentResult {
        if (overview.sector.isNullOrBlank()) {
            logNoData(asset, symbol, "OVERVIEW carried no Sector")
            return EnrichmentResult.NO_DATA
        }

        classificationService.classify(
            asset = asset,
            standardKey = ClassificationStandard.ALPHA,
            sector = overview.sector,
            industry = overview.industry,
            source = AssetClassification.SOURCE_ALPHA_OVERVIEW
        )

        log.info("Classified ${asset.code} as ${overview.sector} / ${overview.industry ?: "N/A"}")
        return EnrichmentResult.ENRICHED
    }

    private fun enrichEtf(asset: Asset): EnrichmentResult {
        val symbol = alphaConfig.getPriceCode(asset)
        val response = alphaProxy.getEtfProfile(symbol, apiKey)
        return when (AlphaResponseBody.of(response, objectMapper)) {
            AlphaResponseBody.RATE_LIMITED -> {
                rateLimited(symbol)
            }
            AlphaResponseBody.ERROR -> {
                logNoData(asset, symbol, "ETF_PROFILE returned an error or empty body")
                EnrichmentResult.FAILED
            }
            AlphaResponseBody.PAYLOAD -> {
                classifyEtfProfile(
                    asset,
                    symbol,
                    objectMapper.readValue(response, AlphaEtfProfileResponse::class.java)
                )
            }
        }
    }

    private fun classifyEtfProfile(
        asset: Asset,
        symbol: String,
        profile: AlphaEtfProfileResponse
    ): EnrichmentResult {
        val hasSectors = !profile.sectors.isNullOrEmpty()
        val hasHoldings = !profile.holdings.isNullOrEmpty()

        if (!hasSectors && !hasHoldings) {
            logNoData(asset, symbol, "ETF_PROFILE carried no sector allocations or holdings")
            return EnrichmentResult.NO_DATA
        }

        val sectorCount = if (hasSectors) etfLookThrough.replaceSectorExposures(asset, profile.sectors) else 0
        val holdingCount = if (hasHoldings) etfLookThrough.replaceTopHoldings(asset, profile.holdings) else 0

        log.info("Added $sectorCount sector exposures and $holdingCount holdings for ${asset.code}")
        return if (sectorCount > 0 || holdingCount > 0) EnrichmentResult.ENRICHED else EnrichmentResult.NO_DATA
    }

    private fun rateLimited(symbol: String): EnrichmentResult {
        log.warn("AlphaVantage rate limit hit while enriching $symbol")
        return EnrichmentResult.RATE_LIMITED
    }

    /**
     * A no-data outcome stamps `classificationCheckedAt` (see `ClassificationRefreshService`), so
     * the asset drops behind the staleness window and is not reconsidered for a week. At DEBUG
     * that left no trace in a normal deployment log — VUAA sat with zero sector exposures and
     * nothing to grep for.
     *
     * The queried symbol is the payload that matters: it is composed from the market's `alpha`
     * alias, so it distinguishes a mapping fault (a symbol the provider has never heard of) from
     * genuine coverage absence (AlphaVantage serves no fundamentals for non-US listings at all).
     */
    private fun logNoData(
        asset: Asset,
        symbol: String,
        reason: String
    ) = log.warn(
        "No classification data from {} for symbol {} ({}:{}) - {}",
        AlphaPriceService.ID,
        symbol,
        asset.market.code,
        asset.code,
        reason
    )

    companion object {
        // Category sets live on ClassificationEnricher - a local copy here silently drifted out of
        // sync with the shared one, which is how MUTUAL FUND ended up handled inconsistently.
        private val EQUITY_CATEGORIES = ClassificationEnricher.EQUITY_CATEGORIES
        private val ETF_CATEGORIES = ClassificationEnricher.ETF_CATEGORIES
    }
}