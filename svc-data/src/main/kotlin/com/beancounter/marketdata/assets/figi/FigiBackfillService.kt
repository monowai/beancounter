package com.beancounter.marketdata.assets.figi

import com.beancounter.marketdata.assets.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.streams.asSequence

/**
 * Fills [com.beancounter.common.model.Asset.compositeFigi] for assets created before it was
 * captured. Enrichment only runs for nameless assets, so existing assets need this pass.
 * Writes the composite FIGI only; name and category stay as they are.
 */
@Service
@ConditionalOnProperty(
    value = ["beancounter.marketdata.provider.figi.enabled"],
    matchIfMissing = true
)
class FigiBackfillService(
    private val assetRepository: AssetRepository,
    private val figiProxy: FigiProxy
) {
    /**
     * Resolve up to [limit] assets on markets with a FIGI alias. Each attempt can cost up to
     * four OpenFIGI calls (one per security type), so keep [limit] modest. An asset FIGI
     * cannot resolve stays null; pass the previous run's [FigiBackfillResult.lastId] as [after]
     * to move past it.
     */
    @Transactional
    fun backfill(
        limit: Int,
        after: String? = null
    ): FigiBackfillResult {
        val candidates =
            assetRepository
                .findWithoutCompositeFigi()
                .use { stream ->
                    stream
                        .asSequence()
                        .filter { after == null || it.id > after }
                        .filter { it.market.aliases[FigiProxy.FIGI] != null }
                        .take(limit)
                        .toList()
                }
        val unresolved = mutableListOf<String>()
        for (asset in candidates) {
            val compositeFigi = figiProxy.find(asset.market, asset.code, asset.id)?.compositeFigi
            if (compositeFigi == null) {
                unresolved.add(asset.id)
            } else {
                asset.compositeFigi = compositeFigi
                assetRepository.save(asset)
            }
        }
        log.info("FIGI backfill: attempted {}, resolved {}", candidates.size, candidates.size - unresolved.size)
        return FigiBackfillResult(
            attempted = candidates.size,
            resolved = candidates.size - unresolved.size,
            unresolved = unresolved,
            lastId = candidates.lastOrNull()?.id
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(FigiBackfillService::class.java)
    }
}

/**
 * Outcome of a [FigiBackfillService.backfill] run. [unresolved] holds the ids FIGI did not
 * match; [lastId] is the cursor for the next run, null when nothing was attempted.
 */
data class FigiBackfillResult(
    val attempted: Int,
    val resolved: Int,
    val unresolved: List<String> = emptyList(),
    val lastId: String? = null
)