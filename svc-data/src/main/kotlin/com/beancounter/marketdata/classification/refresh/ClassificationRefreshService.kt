package com.beancounter.marketdata.classification.refresh

import com.beancounter.common.contracts.BackfillResult
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.classification.ClassificationService
import com.beancounter.marketdata.classification.enricher.ClassificationEnricher
import com.beancounter.marketdata.classification.enricher.EnrichmentResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Service for refreshing asset classification data.
 *
 * Handles both on-demand and scheduled refresh of sector/industry classifications. The refresh is
 * resume-aware: [Asset.classificationCheckedAt] records when an asset's classification was last
 * *attempted*, so a run picks up never-checked / longest-stale assets first rather than
 * re-processing the same head of the list every time - which previously meant a free-tier
 * AlphaVantage key (25 requests/day) never got past the first ~15 assets.
 *
 * Two tunables bound provider quota usage per run:
 *
 * - `beancounter.classification.stale-after-days` (default `7`) - the recheck window. An asset
 *   checked more recently than this is skipped.
 * - `beancounter.classification.batch-limit` (default `20`) - max assets *attempted* per run.
 *   The ETF and equity passes each run daily, so with AlphaVantage alone they share its
 *   25 requests/day free-tier cap; once it is spent, each pass stops on its first rate limit.
 *
 * A [EnrichmentResult.RATE_LIMITED] result stops that provider for the rest of the run - once its
 * quota is exhausted, further calls in the same run cannot succeed, so continuing just burns
 * wall-clock. Assets another provider answers for (see [ClassificationEnricher.providerFor]) still
 * run: otherwise one throttled provider at the head of the queue would starve the rest every day.
 * Rate-limited assets are deliberately NOT stamped with `classificationCheckedAt`, so they are
 * retried first on the next run rather than pushed to the back of the queue.
 */
@Service
class ClassificationRefreshService(
    private val assetRepository: AssetRepository,
    private val classificationEnricher: ClassificationEnricher,
    private val classificationService: ClassificationService,
    @Value("\${beancounter.classification.stale-after-days:7}")
    private val staleAfterDays: Long = 7,
    @Value("\${beancounter.classification.batch-limit:20}")
    private val batchLimit: Int = 20,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(ClassificationRefreshService::class.java)

    /**
     * Refresh sector exposures for ETF assets due a recheck.
     */
    fun refreshEtfSectors(): BackfillResult {
        log.info("Starting ETF sector refresh")
        val cutoff = dateUtils.date.minusDays(staleAfterDays)
        val totalActive = assetRepository.findActiveEtfs().size
        val due = assetRepository.findEtfsDueForClassification(cutoff)
        return processAssets(totalActive, due, "ETF sectors")
    }

    /**
     * Refresh classifications for Equity assets due a recheck.
     */
    fun refreshEquityClassifications(): BackfillResult {
        log.info("Starting Equity classification refresh")
        val cutoff = dateUtils.date.minusDays(staleAfterDays)
        val totalActive = assetRepository.findActiveEquities().size
        val due = assetRepository.findEquitiesDueForClassification(cutoff)
        return processAssets(totalActive, due, "Equity classifications")
    }

    /**
     * Refresh classification for a single asset by ID. Always attempted regardless of the
     * staleness window / batch limit - this is an explicit, on-demand call.
     */
    fun refreshAsset(assetId: String): EnrichmentResult {
        val asset = assetRepository.findById(assetId)
        if (asset.isEmpty) {
            log.warn("Asset not found: $assetId")
            return EnrichmentResult.FAILED
        }

        return attempt(asset.get())
    }

    /**
     * Refresh classification for a single asset by market and code.
     */
    fun refreshAssetByCode(
        marketCode: String,
        assetCode: String
    ): EnrichmentResult {
        val asset = assetRepository.findByMarketCodeAndCode(marketCode, assetCode)
        if (asset.isEmpty) {
            log.warn("Asset not found: $marketCode:$assetCode")
            return EnrichmentResult.FAILED
        }

        return attempt(asset.get())
    }

    /**
     * Enrich up to [limit] assets that a provider can classify but that carry no classification
     * data yet, optionally only those in [category].
     */
    fun backfill(
        category: String?,
        limit: Int
    ): BackfillResult {
        log.info("Starting classification backfill: category=$category, limit=$limit")

        val assets =
            assetRepository
                .findAll()
                .asSequence()
                .filter { asset ->
                    category == null || asset.category.equals(category, ignoreCase = true)
                }.filter { asset ->
                    classificationEnricher.canEnrich(asset)
                }.filter { asset ->
                    !classificationService.isClassified(asset.id, fund = classificationEnricher.isEtf(asset))
                }.take(limit)
                .toList()

        var processed = 0
        var errors = 0
        var noData = 0
        var rateLimited = 0

        for (asset in assets) {
            try {
                when (attempt(asset)) {
                    EnrichmentResult.ENRICHED -> processed++
                    EnrichmentResult.FAILED -> errors++
                    EnrichmentResult.NO_DATA -> noData++
                    EnrichmentResult.RATE_LIMITED -> rateLimited++
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                // Continue processing other assets even if one fails
                errors++
                log.warn("Failed to enrich classification for ${asset.id}: ${e.message}")
            }
        }

        val result =
            BackfillResult(
                processed = processed,
                errors = errors,
                total = assets.size,
                noData = noData,
                rateLimited = rateLimited
            )
        log.info("Classification backfill complete: $result")
        return result
    }

    /**
     * Enrich a single asset and stamp `classificationCheckedAt` (unless rate-limited). Every
     * on-demand entry point, [backfill] included, goes through here rather than calling
     * [ClassificationEnricher] directly: leaving `classificationCheckedAt` unset would make the
     * resume-aware refresh queries treat an already-backfilled asset as never-checked and
     * re-attempt it for no reason.
     */
    private fun attempt(asset: Asset): EnrichmentResult {
        val result = classificationEnricher.enrichClassification(asset)
        stampIfAttempted(asset, result)
        return result
    }

    /**
     * Stamp `classificationCheckedAt` for every outcome except [EnrichmentResult.RATE_LIMITED] -
     * a throttled asset was never really checked, so it must be retried next run rather than
     * pushed behind assets that succeeded or genuinely had no data.
     */
    private fun stampIfAttempted(
        asset: Asset,
        result: EnrichmentResult
    ) {
        if (result == EnrichmentResult.RATE_LIMITED) {
            return
        }
        asset.classificationCheckedAt = dateUtils.date
        assetRepository.save(asset)
    }

    // Routing can touch a provider (SEC's ticker index); a failure falls back to the configured
    // enricher rather than ending the run.
    private fun providerFor(asset: Asset): ClassificationEnricher =
        try {
            classificationEnricher.providerFor(asset)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Could not route ${asset.code} to a classification provider: ${e.message}")
            classificationEnricher
        }

    private fun processAssets(
        totalActive: Int,
        dueAssets: List<Asset>,
        description: String
    ): BackfillResult {
        var processed = 0
        var errors = 0
        var noData = 0
        var rateLimited = 0
        // Assets outside the due list are still within the staleness window - already skipped.
        var skipped = (totalActive - dueAssets.size).coerceAtLeast(0)

        log.info(
            "Processing ${dueAssets.size} due of $totalActive active assets for $description " +
                "(batch-limit=$batchLimit)"
        )

        val exhausted = mutableSetOf<ClassificationEnricher>()
        var attempted = 0
        for (asset in dueAssets) {
            if (attempted >= batchLimit) {
                skipped++
                continue
            }
            val provider = providerFor(asset)
            if (provider in exhausted) {
                skipped++
                continue
            }
            attempted++

            val result =
                try {
                    // The resolved provider, not the chain, so the provider stopped on a rate
                    // limit is the one that answered.
                    provider.enrichClassification(asset)
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception
                ) {
                    // Continue processing other assets even if one fails
                    log.warn("Failed to refresh classification for ${asset.code}: ${e.message}")
                    EnrichmentResult.FAILED
                }

            stampIfAttempted(asset, result)

            when (result) {
                EnrichmentResult.ENRICHED -> {
                    processed++
                }
                EnrichmentResult.NO_DATA -> {
                    noData++
                }
                EnrichmentResult.FAILED -> {
                    errors++
                }
                EnrichmentResult.RATE_LIMITED -> {
                    rateLimited++
                    exhausted += provider
                    log.warn(
                        "$description refresh hit the ${provider::class.simpleName} rate limit at " +
                            "${asset.code}; that provider's remaining assets deferred to next run"
                    )
                }
            }
        }

        val result =
            BackfillResult(
                processed = processed,
                errors = errors,
                total = totalActive,
                noData = noData,
                rateLimited = rateLimited,
                skipped = skipped
            )
        log.info("$description refresh complete: $result")

        return result
    }
}