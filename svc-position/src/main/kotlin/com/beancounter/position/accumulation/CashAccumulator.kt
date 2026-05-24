package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Generic cash accumulation behaviour.
 *
 * These values are unreliable and need to be refactored.
 */
@Service
class CashAccumulator(
    val currencyResolver: CurrencyResolver,
    private val cashUtils: CashUtils = CashUtils()
) {
    private val cashCost = CashCost()

    fun accumulate(
        cashPosition: Position,
        position: Position,
        quantity: BigDecimal,
        trn: Trn
    ): Position {
        // Enforce sign based on transaction type - credits are positive, debits are negative
        // This ensures getTotal() (purchased + sold) calculates correctly regardless of input sign
        val signedQuantity =
            if (TrnType.isCashCredited(trn.trnType)) {
                quantity.abs()
            } else {
                quantity.abs().negate()
            }

        if (TrnType.isCashCredited(trn.trnType)) {
            cashPosition.quantityValues.purchased = cashPosition.quantityValues.purchased.add(signedQuantity)
        } else {
            cashPosition.quantityValues.sold = cashPosition.quantityValues.sold.add(signedQuantity)
        }

        // For cash transactions (DEPOSIT, WITHDRAWAL), the currency comes from the trade itself
        // since the asset IS the cash. For other transactions, use cashCurrency.
        val effectiveCashCurrency = trn.cashCurrency ?: trn.tradeCurrency

        val isCash = cashUtils.isCash(cashPosition.asset)
        applyPool(
            Position.In.TRADE,
            effectiveCashCurrency,
            trn,
            position,
            cashPosition,
            signedQuantity,
            BigDecimal.ONE,
            isCash
        )
        applyPool(
            Position.In.BASE,
            effectiveCashCurrency,
            trn,
            position,
            cashPosition,
            signedQuantity,
            trn.tradeBaseRate,
            isCash
        )
        applyPool(
            Position.In.PORTFOLIO,
            effectiveCashCurrency,
            trn,
            position,
            cashPosition,
            signedQuantity,
            trn.tradePortfolioRate,
            isCash
        )
        return position
    }

    private fun applyPool(
        pool: Position.In,
        cashCurrency: com.beancounter.common.model.Currency,
        trn: Trn,
        position: Position,
        cashPosition: Position,
        signedQuantity: BigDecimal,
        rate: BigDecimal,
        isCash: Boolean
    ) {
        val moneyValues =
            currencyResolver.getMoneyValues(
                pool,
                cashCurrency,
                trn.portfolio,
                position
            )
        cashCost.value(
            moneyValues,
            cashPosition,
            signedQuantity,
            rate
        )
        if (!isCash) {
            // DEPOSIT/WITHDRAWAL targeting a non-cash position (POLICY, RE
            // and friends) is the wizard's "set current balance" path: the
            // user-entered quantity IS both market value AND cost basis, so
            // gain = MV - cost = 0 by construction. Without this CashCost
            // resets cost to zero (cash semantics) and the UI reports a
            // phantom 100% gain.
            applySnapshotCost(moneyValues, rate)
        }
    }

    private fun applySnapshotCost(
        moneyValues: MoneyValues,
        rate: BigDecimal
    ) {
        val net = moneyValues.purchases.subtract(moneyValues.sales).abs()
        val costBasis =
            if (rate == BigDecimal.ZERO) net else net.multiply(BigDecimal.ONE)
        // moneyValues.purchases / sales are already in the pool currency
        // (cashCost.value applied the rate when adding), so just mirror.
        moneyValues.costBasis = costBasis
        moneyValues.costValue = costBasis
        moneyValues.averageCost = if (costBasis > BigDecimal.ZERO) BigDecimal.ONE else BigDecimal.ZERO
    }
}