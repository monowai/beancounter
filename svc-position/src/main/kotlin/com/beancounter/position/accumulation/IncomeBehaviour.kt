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
 * Logic to accumulate an income transaction (interest, salary, etc.) into a position.
 * Tracks income in the dividends (income) column, like DividendBehaviour.
 * Cash crediting is handled by Accumulator.accumulateCash() since INCOME is in creditsCash.
 * Does NOT affect cost basis, market value, or position quantity.
 */
@Service
class IncomeBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver()
) : BaseAccumulationStrategy(currencyResolver) {
    override val supportedType: TrnType
        get() = TrnType.INCOME

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        val currencyContext = createCurrencyContext(trn, position)
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            updateIncome(moneyValues, trn.tradeAmount, rate)
        }
        return position
    }

    private fun updateIncome(
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