package com.beancounter.marketdata.assets.figi

import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.markets.MarketService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * Fills [com.beancounter.common.model.Asset.compositeFigi] for assets created before it was
 * captured. Enrichment only runs for nameless assets, so existing assets need this pass.
 * Writes the composite FIGI only; name and category stay as they are.
 *
 * Deliberately not transactional: no connection is held across OpenFIGI calls, and each
 * resolved asset is written on its own so progress survives a failure mid-batch.
 */
@Service
@ConditionalOnProperty(
    value = ["beancounter.marketdata.provider.figi.enabled"],
    matchIfMissing = true
)
class FigiBackfillService(
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val figiProxy: FigiProxy
) {
    /**
     * Resolve up to [limit] active, unowned assets (clamped to 1..[MAX_LIMIT]) on markets with
     * a FIGI alias, starting after the [after] id. Each attempt can cost up to four OpenFIGI
     * calls. An asset FIGI cannot match stays null; pass [FigiBackfillResult.lastId] as [after]
     * to move past it, or omit [after] to retry from the start. A provider failure stops the
     * run at that asset, reported as [FigiBackfillResult.failed], and [FigiBackfillResult.lastId]
     * stops short of it so the next run starts there.
     */
    fun backfill(
        limit: Int,
        after: String? = null
    ): FigiBackfillResult {
        val figiMarkets =
            marketService
                .getMarketMap()
                .values
                .filter { it.aliases[FigiProxy.FIGI] != null }
                .associateBy { it.code }
        val candidates =
            assetRepository.findWithoutCompositeFigi(
                figiMarkets.keys,
                after ?: "",
                PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT))
            )
        val unresolved = mutableListOf<String>()
        var attempted = 0
        var resolved = 0
        var lastId = after
        var failed: String? = null
        for (asset in candidates) {
            val compositeFigi =
                try {
                    figiProxy.find(figiMarkets.getValue(asset.marketCode), asset.code, asset.id)?.compositeFigi
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: RuntimeException
                ) {
                    log.warn("FIGI backfill stopped at {}", asset.id, e)
                    failed = asset.id
                    break
                }
            if (compositeFigi == null) {
                unresolved.add(asset.id)
            } else if (assetRepository.updateCompositeFigi(asset.id, compositeFigi) == 1) {
                resolved++
            }
            attempted++
            lastId = asset.id
        }
        val result =
            FigiBackfillResult(
                attempted = attempted,
                resolved = resolved,
                unresolved = unresolved,
                lastId = lastId,
                failed = failed
            )
        log.info(
            "FIGI backfill: attempted {}, resolved {}, lastId {}, failed {}, unresolved {}",
            result.attempted,
            result.resolved,
            result.lastId,
            result.failed,
            result.unresolved
        )
        return result
    }

    companion object {
        const val MAX_LIMIT = 200
        private val log = LoggerFactory.getLogger(FigiBackfillService::class.java)
    }
}

/**
 * Outcome of a [FigiBackfillService.backfill] run. [resolved] counts assets written;
 * [unresolved] holds the ids FIGI did not match; [failed] is the asset whose lookup hit a
 * provider error and stopped the run; [lastId] is the cursor for the next run.
 */
data class FigiBackfillResult(
    val attempted: Int,
    val resolved: Int,
    val unresolved: List<String> = emptyList(),
    val lastId: String? = null,
    val failed: String? = null
)