package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Position.In.BASE
import com.beancounter.common.model.Position.In.PORTFOLIO
import com.beancounter.common.model.Position.In.TRADE
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a buy transaction into a position.
 */
@Service
class BuyBehaviour(
    val currencyResolver: CurrencyResolver = CurrencyResolver(),
    val averageCost: AverageCost = AverageCost()
) : AccumulationStrategy {
    override val supportedType: TrnType
        get() = TrnType.BUY

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        position.quantityValues.purchased = position.quantityValues.purchased.add(trn.quantity)
        value(
            trn,
            position,
            TRADE,
            BigDecimal.ONE
        )
        // Use cost of cash for Base and Portfolio(?) rate if cash is impacted
        value(
            trn,
            position,
            PORTFOLIO,
            costRate(
                trn.tradePortfolioRate
            )
        )
        value(
            trn,
            position,
            BASE,
            costRate(
                trn.tradeBaseRate
            )
        )
        return position
    }

    // Routine is flawed and not thought through correctly
    // Simply use the rate from the TRN.
    // Check git history for the original implementation
    private fun costRate(defaultRate: BigDecimal = BigDecimal.ONE): BigDecimal = defaultRate

    private fun value(
        trn: Trn,
        position: Position,
        currency: Position.In,
        rate: BigDecimal
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
        moneyValues.purchases =
            moneyValues.purchases.add(
                multiply(
                    trn.tradeAmount,
                    rate
                )
            )
        moneyValues.costBasis =
            moneyValues.costBasis.add(
                multiply(
                    trn.tradeAmount,
                    rate
                )
            )
        if (moneyValues.costBasis != BigDecimal.ZERO &&
            position.quantityValues
                .getTotal()
                .compareTo(BigDecimal.ZERO) != 0
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