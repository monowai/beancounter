package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType

/**
 * Implement this class to accumulate the transaction into the position on behalf of the portfolio.
 */
interface AccumulationStrategy {
    val supportedType: TrnType

    fun accumulate(
        trn: Trn,
        positions: Positions,
        // Some strategies mutate multiple positions (FX)
        position: Position = positions.getOrCreate(trn.asset, trn.tradeDate),
    ): Position

    fun getCashPosition(
        trn: Trn,
        position: Position,
        positions: Positions,
    ) = if (TrnType.isCash(trn.trnType)) position else positions.getOrCreate(trn.cashAsset!!)
}
