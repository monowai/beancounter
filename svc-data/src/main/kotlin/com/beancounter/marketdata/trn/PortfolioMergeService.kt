package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.PortfolioMergeResponse
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.marketdata.portfolio.PortfolioService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Consolidates one portfolio into another: every transaction in the source is
 * reassigned to the target, then the emptied source portfolio is deleted. Backs
 * the "downgrade to a single portfolio" (zen) wizard.
 *
 * Reuses [PositionMoveService] per distinct asset so FX recalculation (when the
 * portfolios differ in base/settlement currency) and position-cache invalidation
 * are handled exactly as a manual single-asset move would. Same-asset holdings
 * recombine naturally when positions are rebuilt from the reassigned trns.
 *
 * The whole operation is one transaction: if any asset move or the final delete
 * fails, nothing is moved and the source survives.
 */
@Service
@Transactional
class PortfolioMergeService(
    private val trnRepository: TrnRepository,
    private val portfolioService: PortfolioService,
    private val positionMoveService: PositionMoveService
) {
    private val log = LoggerFactory.getLogger(PortfolioMergeService::class.java)

    fun merge(
        sourcePortfolioId: String,
        targetPortfolioId: String
    ): PortfolioMergeResponse {
        if (sourcePortfolioId == targetPortfolioId) {
            throw BusinessException("Cannot merge a portfolio into itself")
        }

        // Resolve both up front: PortfolioService.find enforces ownership/visibility,
        // so a caller can't merge into (or out of) a portfolio they don't own.
        val source = portfolioService.find(sourcePortfolioId)
        val target = portfolioService.find(targetPortfolioId)

        val assetIds =
            trnRepository.findDistinctAssetIdsByPortfolioIds(listOf(source.id)).toList()

        var transactionsMoved = 0
        for (assetId in assetIds) {
            transactionsMoved +=
                positionMoveService
                    .movePosition(
                        PositionMoveRequest(
                            sourcePortfolioId = source.id,
                            targetPortfolioId = target.id,
                            assetId = assetId,
                            maintainCashBalances = false
                        )
                    ).movedCount
        }

        // Source is now empty; delete it (idempotent trn purge + portfolio delete).
        portfolioService.delete(source.id)

        log.info(
            "Merged portfolio {} into {}: {} assets, {} transactions moved; source deleted",
            source.code,
            target.code,
            assetIds.size,
            transactionsMoved
        )

        return PortfolioMergeResponse(
            sourcePortfolioId = source.id,
            targetPortfolioId = target.id,
            assetsMoved = assetIds.size,
            transactionsMoved = transactionsMoved,
            sourceDeleted = true
        )
    }
}