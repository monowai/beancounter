package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import java.math.BigDecimal

/**
 * The rate that takes this trn's trade-currency amount into [pool]'s currency:
 * one for TRADE, the stored trade-to-base rate for BASE, the stored
 * trade-to-portfolio rate for PORTFOLIO.
 */
internal fun Trn.rateFor(pool: Position.In): BigDecimal =
    when (pool) {
        Position.In.TRADE -> BigDecimal.ONE
        Position.In.BASE -> tradeBaseRate
        Position.In.PORTFOLIO -> tradePortfolioRate
    }