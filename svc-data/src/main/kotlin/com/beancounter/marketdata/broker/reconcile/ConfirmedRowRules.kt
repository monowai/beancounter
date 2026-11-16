package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.broker.reconcile.ReconcileService.Companion.MAX_ROWS
import java.math.BigDecimal

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
            rows.isEmpty() -> "A submission with no rows records nothing"
            rows.size > MAX_ROWS -> "A statement holds at most $MAX_ROWS rows, not ${rows.size}"
            else -> namedTwice(rows)?.let { "Asset $it is named more than once" }
        }
    }

    private fun namedTwice(rows: List<ConfirmedRow>): String? {
        val seen = mutableSetOf<String>()
        return rows.firstOrNull { !seen.add(it.assetId) }?.assetId
    }

    private fun isCost(cost: BigDecimal?): Boolean = cost != null && cost.signum() >= 0

    private fun rowProblem(row: ConfirmedRow): String? =
        when {
            row.trnType !in RECORDABLE -> "Asset ${row.assetId}: only ADD or REDUCE is recorded, not ${row.trnType}"
            row.quantity.signum() <= 0 -> "Asset ${row.assetId}: the quantity must be more than zero"
            row.trnType == TrnType.ADD &&
                !isCost(
                    row.cost
                )
            -> "Asset ${row.assetId}: an ADD needs a cost of zero or more"
            else -> null
        }
}