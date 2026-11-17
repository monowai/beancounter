package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.broker.BrokerHoldingAggregation
import com.beancounter.marketdata.trn.broker.TrnBrokerService
import org.springframework.stereotype.Component
import java.time.LocalDate

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
     * A reduction is checked on its own date, not against today's holding: replayed among the
     * portfolio's trns at the broker, it must leave the holding at zero or more from that date
     * on. A back-dated reduction before the trades that built the holding, or one a later sale
     * would take below zero, is refused.
     *
     * @throws BusinessException naming the first asset the submission's portfolio cannot reduce
     * by that much at the submission's broker on the row's date
     */
    fun refuseOverReduction(confirmed: ConfirmedHoldings) {
        val reductions = confirmed.rows.filter { it.trnType == TrnType.REDUCE }
        if (reductions.isEmpty()) return
        val trns = trnBrokerService.settledTrns(confirmed.brokerId)
        val today = DateUtils().date
        for (row in reductions) {
            val date = row.tradeDate ?: confirmed.tradeDate ?: today
            val lowest =
                BrokerHoldingAggregation.lowestAfterReduction(
                    trns,
                    confirmed.portfolioId,
                    row.label,
                    date,
                    row.quantity
                )
            if (lowest.signum() < 0) throw overReduction(row, date, trns, confirmed.portfolioId)
        }
    }

    /** Why [row] cannot be reduced on [date]: too little held then, or a later trade. */
    private fun overReduction(
        row: ConfirmedRow,
        date: LocalDate,
        trns: Collection<Trn>,
        portfolioId: String
    ): BusinessException {
        val held = BrokerHoldingAggregation.heldOn(trns, portfolioId, row.label, date)
        val cannot = "Asset ${row.label}: cannot reduce by ${row.quantity.toPlainString()} on $date"
        return BusinessException(
            if (held < row.quantity) {
                "$cannot, the portfolio holds ${held.toPlainString()} at this broker on that date"
            } else {
                "$cannot, a later trade would take the portfolio's holding at this broker below zero"
            }
        )
    }

    companion object {
        /** The caller reference provider of every transaction recorded from a statement screenshot. */
        const val PROVIDER = "SCREENSHOT"

        /** The prefix of the caller id of every row [owner] records. */
        fun recordedBy(owner: SystemUser): String = "${owner.id}:"
    }
}