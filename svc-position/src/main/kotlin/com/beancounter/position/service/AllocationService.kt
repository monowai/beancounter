package com.beancounter.position.service

import com.beancounter.common.contracts.AllocationData
import com.beancounter.common.contracts.CategoryAllocation
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Service for calculating asset allocation from portfolio positions.
 * Groups positions by report category and calculates allocation percentages.
 */
@Service
class AllocationService {
    /**
     * Calculate allocation breakdown from positions.
     *
     * @param positions The valued positions to analyze
     * @param valueIn Which currency context to use (BASE, PORTFOLIO, or TRADE)
     * @return AllocationData with category breakdown and aggregated allocations
     */
    fun calculateAllocation(
        positions: Positions,
        valueIn: Position.In = Position.In.PORTFOLIO
    ): AllocationData {
        if (!positions.hasPositions()) {
            return AllocationData()
        }

        val totals = positions.totals[valueIn]
        val totalValue = totals?.marketValue ?: BigDecimal.ZERO
        val currency = totals?.currency?.code ?: positions.portfolio.currency.code

        if (totalValue == BigDecimal.ZERO) {
            return AllocationData(currency = currency)
        }

        // Group by report category
        val categoryValues = mutableMapOf<String, BigDecimal>()

        for (position in positions.positions.values) {
            val marketValue = position.moneyValues[valueIn]?.marketValue ?: BigDecimal.ZERO
            if (marketValue > BigDecimal.ZERO) {
                val category = position.asset.effectiveReportCategory
                categoryValues[category] = (categoryValues[category] ?: BigDecimal.ZERO) + marketValue
            }
        }

        // Calculate percentages for each category
        val categoryBreakdown =
            categoryValues
                .map { (category, value) ->
                    val percentage =
                        value
                            .divide(totalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal(100))
                            .setScale(2, RoundingMode.HALF_UP)
                    category to
                        CategoryAllocation(
                            category = category,
                            marketValue = value.setScale(2, RoundingMode.HALF_UP),
                            percentage = percentage
                        )
                }.toMap()

        // Aggregate into retirement plan categories
        val cashAllocation = sumCategoryPercentage(categoryBreakdown, CASH_CATEGORIES)
        val equityAllocation = sumCategoryPercentage(categoryBreakdown, EQUITY_CATEGORIES)
        val housingAllocation = sumCategoryPercentage(categoryBreakdown, HOUSING_CATEGORIES)

        return AllocationData(
            cashAllocation = cashAllocation,
            equityAllocation = equityAllocation,
            housingAllocation = housingAllocation,
            totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
            currency = currency,
            categoryBreakdown = categoryBreakdown
        )
    }

    private fun sumCategoryPercentage(
        breakdown: Map<String, CategoryAllocation>,
        categories: Set<String>
    ): BigDecimal =
        categories
            .mapNotNull { breakdown[it]?.percentage }
            .fold(BigDecimal.ZERO) { acc, pct -> acc + pct }
            .setScale(2, RoundingMode.HALF_UP)

    companion object {
        // Categories that map to Cash allocation
        val CASH_CATEGORIES =
            setOf(
                AssetCategory.REPORT_CASH
            )

        // Categories that map to Equity allocation
        val EQUITY_CATEGORIES =
            setOf(
                AssetCategory.REPORT_EQUITY,
                AssetCategory.REPORT_ETF,
                AssetCategory.REPORT_MUTUAL_FUND
            )

        // Categories that map to Housing/Property allocation
        val HOUSING_CATEGORIES =
            setOf(
                AssetCategory.REPORT_PROPERTY
            )
    }
}