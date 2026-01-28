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
 * Logic to accumulate an expense transaction for real estate/private assets.
 * Tracks expenses against the asset (like dividends).
 * Cash debiting is handled by the Accumulator (since EXPENSE is in debitsCash array).
 * Does NOT affect cost basis or position quantity.
 */
@Service
class ExpenseBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver()
) : BaseAccumulationStrategy(currencyResolver) {
    override val supportedType: TrnType
        get() = TrnType.EXPENSE

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        // Track expense against the asset (like dividends track dividend income)
        val currencyContext = createCurrencyContext(trn, position)
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            updateExpenses(moneyValues, trn.tradeAmount, rate)
        }

        // Cash debiting is handled by Accumulator.accumulateCash() since EXPENSE is in debitsCash
        return position
    }

    private fun updateExpenses(
        moneyValues: MoneyValues,
        tradeAmount: BigDecimal,
        rate: BigDecimal
    ) {
        moneyValues.expenses =
            add(
                moneyValues.expenses,
                multiply(tradeAmount, rate)
            )
    }
}