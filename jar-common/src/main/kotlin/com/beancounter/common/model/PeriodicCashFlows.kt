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
                    BigDecimal.ZERO.minus(it.tradeAmount)
                }
            cashFlows.add(CashFlow(amount.toDouble(), it.tradeDate))
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
