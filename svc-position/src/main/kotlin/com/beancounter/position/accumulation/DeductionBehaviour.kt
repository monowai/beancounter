package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import org.springframework.stereotype.Service

/**
 * Logic to accumulate a deduction transaction (fees, charges, etc.) into a position.
 * Debits cash similar to WITHDRAWAL. A deduction only ever moves cash: against a cash asset
 * it debits that asset; against anything else (a maintenance charge on a property) the asset
 * is untouched and Accumulator's cash leg debits the trn's cash asset.
 */
@Service
class DeductionBehaviour(
    val cashAccumulator: CashAccumulator
) : AccumulationStrategy {
    private val cashUtils = CashUtils()

    override val supportedType: TrnType
        get() = TrnType.DEDUCTION

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        if (!isCashLike(position)) return position
        val cashPosition =
            getCashPosition(
                trn,
                position,
                positions
            )
        val quantity = if (TrnType.isCash(trn.trnType)) trn.quantity else trn.cashAmount
        return cashAccumulator.accumulate(
            cashPosition,
            position,
            quantity,
            trn
        )
    }

    private fun isCashLike(position: Position): Boolean =
        cashUtils.isCash(position.asset) || position.asset.market.code == "CASH"
}