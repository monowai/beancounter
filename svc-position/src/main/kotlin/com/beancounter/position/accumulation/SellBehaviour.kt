package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a sell transaction into a position.
 */
@Service
class SellBehaviour(
    val currencyResolver: CurrencyResolver = CurrencyResolver(),
    val averageCost: AverageCost = AverageCost()
) : AccumulationStrategy {
    override val supportedType: TrnType
        get() = TrnType.SELL

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        var soldQuantity = trn.quantity
        if (soldQuantity.toDouble() > 0) {
            // Sign the quantities
            soldQuantity = BigDecimal.ZERO.subtract(trn.quantity)
        }
        val quantityValues = position.quantityValues
        quantityValues.sold = quantityValues.sold.add(soldQuantity)
        value(
            Position.In.TRADE,
            position,
            BigDecimal.ONE,
            trn
        )
        value(
            Position.In.BASE,
            position,
            trn.tradeBaseRate,
            trn
        )
        value(
            Position.In.PORTFOLIO,
            position,
            trn.tradePortfolioRate,
            trn
        )
        return position
    }

    private fun value(
        currency: Position.In,
        position: Position,
        rate: BigDecimal,
        trn: Trn
    ) {
        val moneyValues =
            position.getMoneyValues(
                currency,
                currencyResolver.resolve(
                    currency,
                    trn.portfolio,
                    trn.tradeCurrency
                )
            )
        moneyValues.sales =
            moneyValues.sales.add(
                multiply(
                    trn.tradeAmount,
                    rate
                )
            )
        if (trn.tradeAmount.compareTo(BigDecimal.ZERO) != 0) {
            val unitCost =
                multiply(
                    trn.tradeAmount,
                    rate
                )?.divide(
                    trn.quantity.abs(),
                    getMathContext()
                )
            val unitProfit = unitCost?.subtract(moneyValues.averageCost)
            val realisedGain = unitProfit!!.multiply(trn.quantity.abs())
            moneyValues.realisedGain =
                add(
                    moneyValues.realisedGain,
                    realisedGain
                )
        }
        if (position.quantityValues.getTotal().compareTo(BigDecimal.ZERO) == 0) {
            moneyValues.costBasis = BigDecimal.ZERO
            moneyValues.costValue = BigDecimal.ZERO
            moneyValues.averageCost = BigDecimal.ZERO
            moneyValues.marketValue = BigDecimal.ZERO
            moneyValues.unrealisedGain = BigDecimal.ZERO
            position.quantityValues.sold = BigDecimal.ZERO
            position.quantityValues.adjustment = BigDecimal.ZERO
            position.quantityValues.purchased = BigDecimal.ZERO
        }
        // If quantity changes, we need to update the cost Value
        moneyValues.costValue =
            averageCost.getCostValue(
                position,
                moneyValues
            )
    }
}