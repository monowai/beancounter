package com.beancounter.position.accumulation

import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.position.marketvalue.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

private const val AVERAGE_COST_SCALE = 10

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
        val movement = CashMovement(effectiveCashCurrency, trn, position, cashPosition, signedQuantity, needsSnapshot)
        Position.In.entries.forEach { applyPool(it, movement) }
        return position
    }

    private fun applyPool(
        pool: Position.In,
        movement: CashMovement
    ) {
        val moneyValues =
            currencyResolver.getMoneyValues(
                pool,
                movement.cashCurrency,
                movement.trn.portfolio,
                movement.position
            )
        cashCost.value(
            moneyValues,
            movement.cashPosition,
            movement.signedQuantity,
            movement.trn.rateFor(pool)
        )
        if (movement.applySnapshot) {
            // Non-cash DEPOSIT/WITHDRAWAL targeting this asset's position —
            // the wizard's "set current balance" path. Snapshot cost so MV
            // − cost = 0 by construction instead of the phantom 100% gain
            // CashCost's zero-cost cash semantics produced.
            applySnapshotCost(moneyValues, movement.cashPosition.quantityValues.getTotal())
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
                net.divide(totalQuantity.abs(), AVERAGE_COST_SCALE, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
    }
}

/**
 * One cash movement as [CashAccumulator.accumulate] applies it to every currency
 * pool: the signed quantity moved on [cashPosition], in [cashCurrency], on behalf
 * of [position]. [applySnapshot] marks a non-cash asset whose cost is its balance.
 */
private class CashMovement(
    val cashCurrency: Currency,
    val trn: Trn,
    val position: Position,
    val cashPosition: Position,
    val signedQuantity: BigDecimal,
    val applySnapshot: Boolean
)