package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import org.springframework.stereotype.Service

/**
 * Accumulates a dividend transaction event into a position, stamping the last dividend date.
 */
@Service
class DividendBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver()
) : AmountBehaviour(currencyResolver, MoneyValues::dividends) {
    override val supportedType: TrnType
        get() = TrnType.DIVI

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        position.dateValues.lastDividend = trn.tradeDate
        return super.accumulate(trn, positions, position)
    }
}