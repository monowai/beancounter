package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import org.springframework.stereotype.Service

/**
 * Logic to accumulate an income transaction (interest, salary, etc.) into a position.
 * Credits cash similar to DEPOSIT.
 */
@Service
class IncomeBehaviour(
    val cashAccumulator: CashAccumulator
) : AccumulationStrategy {
    override val supportedType: TrnType
        get() = TrnType.INCOME

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
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
}