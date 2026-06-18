package com.beancounter.common.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Manage cash flows from transactions and positions.
 */
class PeriodicCashFlows {
    val cashFlows: MutableList<CashFlow> = mutableListOf()
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
                cashFlows.add(
                    CashFlow(
                        amount,
                        it.tradeDate
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
                    position.moneyValues[Position.In.TRADE]!!.marketValue.toDouble(),
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

    fun clear() {
        cashFlows.clear()
    }
}