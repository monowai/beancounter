package com.beancounter.common.model

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
            cashFlows.add(CashFlow(it.cashAmount.toDouble(), it.tradeDate))
        }
    }

    fun add(
        position: Position,
        date: LocalDate,
    ) {
        if (position.quantityValues.hasPosition()) {
            cashFlows.add(CashFlow(position.moneyValues[Position.In.TRADE]!!.marketValue.toDouble(), date))
        }
    }

    fun addAll(toAdd: List<CashFlow>) {
        val dateToAmountMap =
            (cashFlows + toAdd).groupBy(CashFlow::date)
                .mapValues { (_, cashFlows) -> cashFlows.sumOf(CashFlow::amount) }
        cashFlows.clear()
        cashFlows.addAll(dateToAmountMap.map { (date, amount) -> CashFlow(amount, date) })
    }
}
