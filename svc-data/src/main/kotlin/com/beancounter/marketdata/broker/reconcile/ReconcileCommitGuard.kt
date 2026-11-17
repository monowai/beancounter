package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.broker.TrnBrokerService
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * What a confirmed submission must satisfy against what is already recorded.
 */
@Component
class ReconcileCommitGuard(
    private val trnRepository: TrnRepository,
    private val trnBrokerService: TrnBrokerService
) {
    /**
     * The repository query spans every user, so the match is narrowed to the caller here:
     * someone else's upload id must neither block this one nor reveal that it exists, even
     * when they recorded it into a portfolio the caller owns and shares with them. The caller
     * id of every recorded row starts with the id of the user who recorded it.
     *
     * @throws BusinessException if [owner] has already recorded [uploadId]
     */
    fun refuseRecorded(
        uploadId: String,
        owner: SystemUser
    ) {
        val recorded =
            trnRepository
                .findByCallerRefProviderAndCallerRefBatch(PROVIDER, uploadId)
                // Found by its caller reference, so every row carries one.
                .any { checkNotNull(it.callerRef).callerId.startsWith(recordedBy(owner)) }
        if (recorded) throw BusinessException("Upload $uploadId is already recorded")
    }

    /**
     * @throws BusinessException naming the first asset reduced by more than the
     * submission's portfolio holds of it at the submission's broker
     */
    fun refuseOverReduction(confirmed: ConfirmedHoldings) {
        val reductions = confirmed.rows.filter { it.trnType == TrnType.REDUCE }
        if (reductions.isEmpty()) return
        val held = heldByAsset(confirmed)
        val over = reductions.firstOrNull { it.quantity > (held[it.label] ?: BigDecimal.ZERO) } ?: return
        throw BusinessException(
            "Asset ${over.label}: cannot reduce by ${over.quantity.toPlainString()}, the portfolio holds " +
                "${(held[over.label] ?: BigDecimal.ZERO).toPlainString()} at this broker"
        )
    }

    /** Other portfolios' holdings at the broker are not this submission's to reduce. */
    private fun heldByAsset(confirmed: ConfirmedHoldings): Map<String, BigDecimal> =
        trnBrokerService
            .getBrokerHoldings(confirmed.brokerId)
            .holdings
            .associate { position ->
                position.assetId to
                    position.portfolioGroups
                        .filter { it.portfolioId == confirmed.portfolioId }
                        .fold(BigDecimal.ZERO) { total, group -> total.add(group.quantity) }
            }

    companion object {
        /** The caller reference provider of every transaction recorded from a statement screenshot. */
        const val PROVIDER = "SCREENSHOT"

        /** The prefix of the caller id of every row [owner] records. */
        fun recordedBy(owner: SystemUser): String = "${owner.id}:"
    }
}