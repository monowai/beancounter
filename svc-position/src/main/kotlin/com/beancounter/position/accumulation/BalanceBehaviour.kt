package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to Add a balance transaction into a position.
 * These are psuedo cash transactions that do not support cashAssetId.
 */
@Service
class BalanceBehaviour(
    val currencyResolver: CurrencyResolver,
    private val cashUtils: CashUtils = CashUtils()
) : AccumulationStrategy {
    private val cashCost = CashCost()
    override val supportedType: TrnType
        get() = TrnType.BALANCE

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        position.quantityValues.purchased = trn.tradeAmount
        val isCash = cashUtils.isCash(position.asset)
        applyMoneyValues(
            Position.In.BASE,
            trn,
            position,
            trn.tradeBaseRate,
            isCash
        )
        applyMoneyValues(
            Position.In.PORTFOLIO,
            trn,
            position,
            trn.tradePortfolioRate,
            isCash
        )
        applyMoneyValues(
            Position.In.TRADE,
            trn,
            position,
            BigDecimal.ZERO,
            isCash
        )
        return position
    }

    private fun applyMoneyValues(
        pool: Position.In,
        trn: Trn,
        position: Position,
        rate: BigDecimal,
        isCash: Boolean
    ) {
        val moneyValues =
            currencyResolver.getMoneyValues(
                pool,
                trn.tradeCurrency,
                trn.portfolio,
                position
            )
        cashCost.value(
            moneyValues,
            position,
            trn.tradeAmount,
            rate
        )
        if (!isCash) {
            setSnapshotCost(
                moneyValues,
                trn.tradeAmount,
                rate
            )
        }
    }

    /**
     * Non-cash BALANCE = snapshot of accumulated contributions. Cost basis
     * tracks the snapshot amount so MV - cost = 0 by construction; this
     * stops pension/CPF positions reporting a phantom 100% gain (the prior
     * code reused CashCost which keeps cost at zero for cash semantics).
     */
    private fun setSnapshotCost(
        moneyValues: MoneyValues,
        tradeAmount: BigDecimal,
        rate: BigDecimal
    ) {
        val amount = MathUtils.multiply(tradeAmount, rate) ?: return
        moneyValues.costBasis = amount.abs()
        moneyValues.costValue = amount.abs()
        moneyValues.averageCost = BigDecimal.ONE
    }
}