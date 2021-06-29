package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn

/**
 * Implement this class to accumulate the transaction into the position on behalf of the portfolio.
 */
interface AccumulationStrategy {
    fun accumulate(trn: Trn, portfolio: Portfolio, position: Position)
}
