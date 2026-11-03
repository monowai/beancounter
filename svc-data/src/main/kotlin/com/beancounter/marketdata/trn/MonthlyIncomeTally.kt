package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import java.math.BigDecimal
import java.time.YearMonth

private const val TOP_CONTRIBUTORS_LIMIT = 10

/** Group key for income whose asset lacks the grouped attribute. */
internal const val UNKNOWN_GROUP = "Unknown"

/**
 * One income report in the making: monthly totals over [period], and per-group totals
 * keyed by [groupBy] ("assetClass", "sector", "currency" or "market"). Amounts arrive
 * already in [currency].
 */
class MonthlyIncomeTally(
    private val period: ClosedRange<YearMonth>,
    private val groupBy: String,
    private val currency: String,
    private val assetMetaData: Map<String, AssetMetaData>
) {
    private val months: List<YearMonth> =
        generateSequence(period.start) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(period.endInclusive) }
            .toList()
    private val monthlyTotals = months.associateWith { BigDecimal.ZERO }.toMutableMap()
    private val groups = mutableMapOf<String, GroupAggregation>()

    private class GroupAggregation(
        val groupKey: String,
        var totalIncome: BigDecimal = BigDecimal.ZERO,
        val monthlyData: MutableList<MonthlyIncomeData>,
        val assetIncomes: MutableMap<String, IncomeContributor> = mutableMapOf()
    )

    fun add(
        trn: Trn,
        amount: BigDecimal
    ) {
        val trnMonth = YearMonth.from(trn.tradeDate)
        val monthIndex = months.indexOf(trnMonth)

        monthlyTotals[trnMonth] = monthlyTotals.getOrDefault(trnMonth, BigDecimal.ZERO).add(amount)

        val assetId = trn.asset.id
        val meta = assetMetaData[assetId]
        val groupKey = groupKey(meta, trn)

        val groupAgg =
            groups.getOrPut(groupKey) {
                GroupAggregation(
                    groupKey = groupKey,
                    monthlyData = months.map { MonthlyIncomeData(it.toString(), BigDecimal.ZERO) }.toMutableList()
                )
            }
        groupAgg.totalIncome = groupAgg.totalIncome.add(amount)
        if (monthIndex >= 0) {
            val existingData = groupAgg.monthlyData[monthIndex]
            groupAgg.monthlyData[monthIndex] =
                MonthlyIncomeData(existingData.yearMonth, existingData.income.add(amount))
        }

        val assetContrib =
            groupAgg.assetIncomes.getOrPut(assetId) {
                IncomeContributor(assetId, meta?.code ?: assetId, meta?.name, BigDecimal.ZERO)
            }
        assetContrib.totalIncome = assetContrib.totalIncome.add(amount)
    }

    fun toResponse(): MonthlyIncomeResponse {
        val totalIncome = monthlyTotals.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        val monthlyDataList = months.map { MonthlyIncomeData(it.toString(), monthlyTotals[it] ?: BigDecimal.ZERO) }

        val groupIncomeList =
            groups.values
                .map { agg ->
                    IncomeGroupData(
                        groupKey = agg.groupKey,
                        totalIncome = agg.totalIncome,
                        monthlyData = agg.monthlyData,
                        topContributors =
                            agg.assetIncomes.values
                                .sortedByDescending { it.totalIncome }
                                .take(TOP_CONTRIBUTORS_LIMIT)
                    )
                }.sortedByDescending { it.totalIncome }

        return MonthlyIncomeResponse(
            startMonth = period.start.toString(),
            endMonth = period.endInclusive.toString(),
            currency = currency,
            totalIncome = totalIncome,
            groupBy = groupBy,
            months = monthlyDataList,
            groups = groupIncomeList
        )
    }

    private fun groupKey(
        meta: AssetMetaData?,
        trn: Trn
    ): String =
        when (groupBy) {
            "sector" -> meta?.sector?.ifBlank { UNKNOWN_GROUP } ?: UNKNOWN_GROUP
            "currency" -> trn.tradeCurrency.code
            "market" -> meta?.marketCode ?: UNKNOWN_GROUP
            else -> meta?.category?.ifBlank { UNKNOWN_GROUP } ?: UNKNOWN_GROUP
        }
}