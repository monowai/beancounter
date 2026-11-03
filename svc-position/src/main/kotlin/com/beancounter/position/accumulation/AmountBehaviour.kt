package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import java.math.BigDecimal
import kotlin.reflect.KMutableProperty1

/**
 * Accrues a transaction's trade amount onto one MoneyValues field, in every reporting currency.
 * Does NOT affect cost basis or position quantity. Accumulator.accumulateCash() moves any cash.
 */
abstract class AmountBehaviour(
    currencyResolver: CurrencyResolver,
    private val accrues: KMutableProperty1<MoneyValues, BigDecimal>
) : BaseAccumulationStrategy(currencyResolver) {
    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        val currencyContext = createCurrencyContext(trn, position)
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            accrues.set(moneyValues, add(accrues.get(moneyValues), multiply(trn.tradeAmount, rate)))
        }
        return position
    }
}