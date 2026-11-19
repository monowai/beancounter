package com.beancounter.common.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Manage cash flows from transactions and positions.
 */
class PeriodicCashFlows {
    /** The current holding cycle: from the position's open, or its re-entry after a sell-out. */
    var cashFlows: MutableList<CashFlow> = mutableListOf()

    /** Cash flows of the cycles that ended in a sell-out, kept for the lifetime IRR. */
    val closedCycles: MutableList<CashFlow> = mutableListOf()
    private var firstTransactionDate: LocalDate? = null

    fun add(trn: Trn) {
        trn.takeIf { it.asset.market.code != "CASH" }?.let {
            if (firstTransactionDate == null) {
                firstTransactionDate = it.tradeDate
            }
            val amount =
                if (it.cashAmount.compareTo(BigDecimal.ZERO) != 0) {
                    it.cashAmount.toDouble()
                } else {
                    BigDecimal.ZERO.minus(it.tradeAmount).toDouble()
                }
            if (amount != 0.0) {
                // Dated when the cost was paid: an ADD's cost date can precede its trade date.
                cashFlows.add(
                    CashFlow(
                        amount,
                        it.costPaidOn
                    )
                )
            }
        }
    }

    /**
     * Add a terminal cash flow for valuation purposes.
     *
     * For active positions: adds the current market value as a positive cash flow (what you'd get if sold today)
     * For sold-out positions: no cash flow is added, but historical cash flows are preserved for XIRR calculation
     */
    fun add(
        position: Position,
        date: LocalDate
    ) {
        if (position.quantityValues.hasPosition()) {
            cashFlows.add(
                CashFlow(
                    position.getMoneyValues(Position.In.TRADE).marketValue.toDouble(),
                    date
                )
            )
        }
        // Don't clear cash flows for sold-out positions - we need them for XIRR calculation
    }

    fun addAll(toAdd: List<CashFlow>) {
        val dateToAmountMap =
            (cashFlows + toAdd)
                .groupBy(CashFlow::date)
                .mapValues { (_, cashFlows) -> cashFlows.sumOf(CashFlow::amount) }
        clear()
        cashFlows.addAll(
            dateToAmountMap.map { (date, amount) ->
                CashFlow(
                    amount,
                    date
                )
            }
        )
    }

    /** Every cash flow the asset has had: closed cycles, then the current one. */
    fun lifetime(): PeriodicCashFlows = PeriodicCashFlows().also { it.cashFlows.addAll(closedCycles + cashFlows) }

    /** Close the current cycle after a sell-out; the re-entry starts a new one. */
    fun startNewCycle() {
        closedCycles.addAll(cashFlows)
        cashFlows.clear()
    }

    fun clear() {
        cashFlows.clear()
    }
}