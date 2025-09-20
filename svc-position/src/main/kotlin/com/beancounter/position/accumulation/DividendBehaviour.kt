package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Optimized logic to accumulate a dividend transaction event into a position.
 * Uses BaseAccumulationStrategy to eliminate redundant currency resolution.
 */
@Service
class DividendBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver()
) : BaseAccumulationStrategy(currencyResolver) {
    override val supportedType: TrnType
        get() = TrnType.DIVI

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        position.dateValues.lastDividend = trn.tradeDate

        // Create optimized currency context once instead of 3 separate resolutions
        val currencyContext = createCurrencyContext(trn, position)

        // Apply dividend updates across all currencies efficiently
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            updateDividends(moneyValues, trn.tradeAmount, rate)
        }

        return position
    }

    private fun updateDividends(
        moneyValues: MoneyValues,
        tradeAmount: BigDecimal,
        rate: BigDecimal
    ) {
        moneyValues.dividends =
            add(
                moneyValues.dividends,
                multiply(tradeAmount, rate)
            )
    }
}