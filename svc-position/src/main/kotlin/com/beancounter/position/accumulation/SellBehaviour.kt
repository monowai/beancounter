package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a sell transaction into a position.
 */
@Service
class SellBehaviour : AccumulationStrategy {
    private val currencyResolver = CurrencyResolver()
    private val averageCost = AverageCost()
    override fun accumulate(trn: Trn, positions: Positions, position: Position, portfolio: Portfolio): Position {
        var soldQuantity = trn.quantity
        if (soldQuantity.toDouble() > 0) {
            // Sign the quantities
            soldQuantity = BigDecimal.ZERO.subtract(trn.quantity)
        }
        val quantityValues = position.quantityValues
        quantityValues.sold = quantityValues.sold.add(soldQuantity)
        value(trn, portfolio, position, Position.In.TRADE, BigDecimal.ONE)
        value(trn, portfolio, position, Position.In.BASE, trn.tradeBaseRate)
        value(
            trn,
            portfolio,
            position,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate,
        )
        return position
    }

    private fun value(
        trn: Trn,
        portfolio: Portfolio,
        position: Position,
        `in`: Position.In,
        rate: BigDecimal?,
    ) {
        val moneyValues = position.getMoneyValues(
            `in`,
            currencyResolver.resolve(`in`, portfolio, trn.tradeCurrency),
        )
        moneyValues.sales = moneyValues.sales.add(
            divide(trn.tradeAmount, rate),
        )
        if (trn.tradeAmount.compareTo(BigDecimal.ZERO) != 0) {
            val unitCost = divide(trn.tradeAmount, rate)
                .divide(trn.quantity.abs(), getMathContext())
            val unitProfit = unitCost.subtract(moneyValues.averageCost)
            val realisedGain = unitProfit.multiply(trn.quantity.abs())
            moneyValues.realisedGain = add(moneyValues.realisedGain, realisedGain)
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
        averageCost.setCostValue(position, moneyValues)
    }
}
