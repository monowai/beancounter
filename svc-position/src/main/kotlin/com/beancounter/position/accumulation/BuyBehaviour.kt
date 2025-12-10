package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Optimized logic to accumulate a buy transaction into a position.
 * Uses BaseAccumulationStrategy to eliminate redundant currency resolution.
 */
@Service
class BuyBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver(),
    val averageCost: AverageCost = AverageCost()
) : BaseAccumulationStrategy(currencyResolver) {
    override val supportedType: TrnType
        get() = TrnType.BUY

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        // Clear closed date if reopening a previously closed position
        if (position.dateValues.closed != null) {
            position.dateValues.closed = null
        }
        position.quantityValues.purchased = position.quantityValues.purchased.add(trn.quantity)

        // Create optimized currency context once instead of 3 separate resolutions
        val currencyContext = createCurrencyContext(trn, position)

        // Apply buy updates across all currencies efficiently
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            updatePurchases(moneyValues, trn.tradeAmount, rate, position)
        }

        return position
    }

    private fun updatePurchases(
        moneyValues: com.beancounter.common.model.MoneyValues,
        tradeAmount: BigDecimal,
        rate: BigDecimal,
        position: Position
    ) {
        val purchaseAmount = multiply(tradeAmount, rate)

        moneyValues.purchases = moneyValues.purchases.add(purchaseAmount)
        moneyValues.costBasis = moneyValues.costBasis.add(purchaseAmount)

        if (moneyValues.costBasis != ZERO &&
            position.quantityValues.getTotal().compareTo(ZERO) != 0
        ) {
            moneyValues.averageCost =
                averageCost.value(
                    moneyValues.costBasis,
                    position.quantityValues.getTotal()
                )
        }
        moneyValues.costValue =
            averageCost.getCostValue(
                position,
                moneyValues
            )
    }
}