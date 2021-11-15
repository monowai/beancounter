package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType

/**
 * Implement this class to accumulate the transaction into the position on behalf of the portfolio.
 */
interface AccumulationStrategy {
    fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position = positions[trn.asset, trn.tradeDate], // Some strategies mutate multiple positions (FX)
        portfolio: Portfolio = positions.portfolio
    ): Position

    fun getCashPosition(
        trn: Trn,
        position: Position,
        positions: Positions
    ) = if (TrnType.isCash(trn.trnType)) position else positions[trn.cashAsset]
}
