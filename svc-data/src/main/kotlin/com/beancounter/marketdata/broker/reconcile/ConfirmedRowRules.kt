package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.broker.reconcile.ReconcileService.Companion.MAX_ROWS
import java.math.BigDecimal
import java.time.LocalDate

/**
 * What a confirmed submission must satisfy on its own, before anything stored is consulted.
 */
internal object ConfirmedRowRules {
    private val RECORDABLE = setOf(TrnType.ADD, TrnType.REDUCE)

    /** @throws BusinessException naming the first rule [confirmed] breaks */
    fun check(confirmed: ConfirmedHoldings) {
        val problem = submissionProblem(confirmed) ?: confirmed.rows.firstNotNullOfOrNull { rowProblem(it) }
        if (problem != null) throw BusinessException(problem)
    }

    private fun submissionProblem(confirmed: ConfirmedHoldings): String? {
        val rows = confirmed.rows
        return when {
            confirmed.uploadId.isBlank() -> "A submission needs the id of its upload"
            rows.isEmpty() && confirmed.cash == null -> "A submission with no rows and no cash records nothing"
            rows.size > MAX_ROWS -> "A statement holds at most $MAX_ROWS rows, not ${rows.size}"
            // The rows are recorded as settled, and a trade does not settle before its date.
            isFuture(confirmed.tradeDate) -> "The trade date ${confirmed.tradeDate} is after today"
            else -> confirmed.cash?.let { cashProblem(it) } ?: namedTwice(rows)?.let { namedTwiceProblem(it) }
        }
    }

    private fun cashProblem(cash: ConfirmedCash): String? =
        when {
            cash.amount.signum() <= 0 -> "The cash balance must be more than zero, not ${cash.amount.toPlainString()}"
            cash.currency.isBlank() -> "The cash balance needs a currency"
            else -> null
        }

    /**
     * Once every row carries its asset's id: one asset named by id in one row and by market and
     * code in another is still named twice.
     *
     * @throws BusinessException naming the asset
     */
    fun checkResolved(resolved: ConfirmedHoldings) {
        namedTwice(resolved.rows)?.let { throw BusinessException(namedTwiceProblem(it)) }
    }

    private fun namedTwiceProblem(label: String) = "Asset $label is named more than once"

    private fun namedTwice(rows: List<ConfirmedRow>): String? {
        val seen = mutableSetOf<String>()
        return rows.firstOrNull { !seen.add(it.label) }?.label
    }

    private fun isFuture(tradeDate: LocalDate?): Boolean =
        tradeDate != null && DateUtils().isFutureEverywhere(tradeDate)

    private fun isCost(cost: BigDecimal?): Boolean = cost != null && cost.signum() >= 0

    private fun rowProblem(row: ConfirmedRow): String? =
        when {
            !row.named -> "A row names its asset by id, or by market and code"
            row.trnType !in RECORDABLE -> "Asset ${row.label}: only ADD or REDUCE is recorded, not ${row.trnType}"
            row.quantity.signum() <= 0 -> "Asset ${row.label}: the quantity must be more than zero"
            isFuture(row.tradeDate) -> "Asset ${row.label}: the trade date ${row.tradeDate} is after today"
            row.trnType == TrnType.ADD &&
                !isCost(
                    row.cost
                )
            -> "Asset ${row.label}: an ADD needs a cost of zero or more"
            else -> null
        }
}