package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.model.TrnType.DEPOSIT
import com.beancounter.common.model.TrnType.DIVI
import com.beancounter.common.model.TrnType.FX_BUY
import com.beancounter.common.model.TrnType.WITHDRAWAL
import com.beancounter.common.utils.DateUtils
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service

/**
 * Convenience service to apply the correct AccumulationLogic to the transaction
 * and calculate the value of a position.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Service
@Import(
    DateUtils::class,
    TrnBehaviourFactory::class,
)
class Accumulator(private val trnBehaviourFactory: TrnBehaviourFactory) {
    /**
     * Add the transaction into the Positions accounting for Cash.
     *
     * @param trn      Transaction to add
     * @param positions Position collection to accumulate into
     * @return the position affected by Trn.assetId
     */
    fun accumulate(trn: Trn, positions: Positions): Position {
        val position = positions[trn.asset, trn.tradeDate]
        if (trn.trnType !== DIVI) {
            isDateSequential(trn, position)
        }
        val accumulationStrategy = trnBehaviourFactory[trn.trnType]
        accumulationStrategy.accumulate(trn, positions)
        position.dateValues.last = trn.tradeDate
        if (isCashAccumulated(trn)) {
            accumulateCash(trn, positions)
        }
        return position // The impacted Asset position
    }

    private fun isCashAccumulated(trn: Trn): Boolean {
        if (trn.cashAsset != null && TrnType.isCashImpacted(trn.trnType)) {
            if (trn.trnType == FX_BUY || trn.trnType == DEPOSIT || trn.trnType == WITHDRAWAL) {
                return false // Don't accumulate cash twice
            }
            return true
        }
        return false
    }

    private fun accumulateCash(trn: Trn, positions: Positions) {
        val cashPosition = positions[trn.cashAsset, trn.tradeDate]
        if (TrnType.isCashCredited(trn.trnType)) {
            trnBehaviourFactory[DEPOSIT].accumulate(trn, positions, cashPosition)
        } else if (TrnType.isCashDebited(trn.trnType)) {
            trnBehaviourFactory[WITHDRAWAL].accumulate(trn, positions, cashPosition)
        }
        cashPosition.dateValues.last = trn.tradeDate
    }

    private fun isDateSequential(trn: Trn, position: Position) {
        var validDate = false
        val tradeDate = trn.tradeDate
        val positionDate = position.dateValues.last
        if (positionDate == null || positionDate <= tradeDate) {
            validDate = true
        }
        if (!validDate) {
            throw BusinessException(
                String.format(
                    "Date sequence problem %s",
                    trn.toString()
                )
            )
        }
    }
}
