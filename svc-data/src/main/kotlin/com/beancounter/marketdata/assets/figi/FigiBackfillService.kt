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
 * resolved asset is saved on its own so progress survives a failure mid-batch.
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
     * Resolve up to [limit] assets (clamped to 1..[MAX_LIMIT]) on markets with a FIGI alias,
     * starting after the [after] id. Each attempt can cost up to four OpenFIGI calls. An asset
     * FIGI cannot resolve stays null; pass [FigiBackfillResult.lastId] as [after] to move past
     * it, or omit [after] to retry from the start.
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
        for (asset in candidates) {
            val compositeFigi =
                figiProxy.find(figiMarkets.getValue(asset.marketCode), asset.code, asset.id)?.compositeFigi
            if (compositeFigi == null) {
                unresolved.add(asset.id)
            } else {
                asset.compositeFigi = compositeFigi
                assetRepository.save(asset)
            }
        }
        val result =
            FigiBackfillResult(
                attempted = candidates.size,
                resolved = candidates.size - unresolved.size,
                unresolved = unresolved,
                lastId = candidates.lastOrNull()?.id
            )
        log.info(
            "FIGI backfill: attempted {}, resolved {}, lastId {}, unresolved {}",
            result.attempted,
            result.resolved,
            result.lastId,
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
 * Outcome of a [FigiBackfillService.backfill] run. [unresolved] holds the ids FIGI did not
 * match, whether for no match or a provider error; [lastId] is the cursor for the next run,
 * null when nothing was attempted.
 */
data class FigiBackfillResult(
    val attempted: Int,
    val resolved: Int,
    val unresolved: List<String> = emptyList(),
    val lastId: String? = null
)