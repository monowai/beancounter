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

        // Snapshot cost is only relevant when this CashAccumulator call IS
        // the side that mutates the asset position — i.e. DEPOSIT /
        // WITHDRAWAL / DEDUCTION where the asset itself is being acted on
        // (cashPosition === position). For the cash-side leg of a BUY/SELL
        // the cashPosition is a separate cash asset and CashCost's zeros
        // are the right behaviour. Also requires the position's asset to
        // be non-cash (POLICY/RE), captured by either CashUtils.isCash or
        // a CASH market code (some test fixtures construct Assets without
        // setting category, so isCash alone is not enough).
        val isCashSide = cashPosition !== position
        val isCashAsset =
            cashUtils.isCash(cashPosition.asset) ||
                cashPosition.asset.market.code == "CASH"
        val needsSnapshot = !isCashSide && !isCashAsset
        applyPool(
            Position.In.TRADE,
            effectiveCashCurrency,
            trn,
            position,
            cashPosition,
            signedQuantity,
            BigDecimal.ONE,
            needsSnapshot
        )
        applyPool(
            Position.In.BASE,
            effectiveCashCurrency,
            trn,
            position,
            cashPosition,
            signedQuantity,
            trn.tradeBaseRate,
            needsSnapshot
        )
        applyPool(
            Position.In.PORTFOLIO,
            effectiveCashCurrency,
            trn,
            position,
            cashPosition,
            signedQuantity,
            trn.tradePortfolioRate,
            needsSnapshot
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
        applySnapshot: Boolean
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
        if (applySnapshot) {
            // Non-cash DEPOSIT/WITHDRAWAL targeting this asset's position —
            // the wizard's "set current balance" path. Snapshot cost so MV
            // − cost = 0 by construction instead of the phantom 100% gain
            // CashCost's zero-cost cash semantics produced.
            applySnapshotCost(moneyValues, cashPosition.quantityValues.getTotal())
        }
    }

    private fun applySnapshotCost(
        moneyValues: MoneyValues,
        totalQuantity: BigDecimal
    ) {
        // sales is already stored as a signed-negative figure for
        // WITHDRAWAL/DEDUCTION, so the net is purchases + sales (not
        // purchases - sales which double-counts the outflow).
        val net = moneyValues.purchases.add(moneyValues.sales).abs()
        moneyValues.costBasis = net
        moneyValues.costValue = net
        moneyValues.averageCost =
            if (totalQuantity.signum() != 0) {
                net.divide(totalQuantity.abs(), 10, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
    }
}