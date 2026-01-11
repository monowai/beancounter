package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.AllocationData
import com.beancounter.common.contracts.CategoryAllocation
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Service for calculating asset allocation from portfolio positions.
 * Groups positions by report category and calculates allocation percentages.
 */
@Service
class AllocationService(
    private val tokenService: TokenService
) {
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

    /**
     * Convert allocation values to a target currency.
     *
     * @param allocation The source allocation data
     * @param targetCurrency The target currency code (e.g., "NZD")
     * @param fxService The FX service to fetch rates
     * @param rateDate The date for FX rate lookup (defaults to today)
     * @return AllocationData with values converted to target currency
     */
    fun convertCurrency(
        allocation: AllocationData,
        targetCurrency: String,
        fxService: FxService,
        rateDate: String = "today"
    ): AllocationData {
        if (allocation.totalValue == BigDecimal.ZERO) {
            return allocation.copy(currency = targetCurrency)
        }

        val sourceCurrency = allocation.currency
        val pair = IsoCurrencyPair(from = sourceCurrency, to = targetCurrency)

        val fxRequest = FxRequest(rateDate = rateDate).add(pair)
        val fxResponse = fxService.getRates(fxRequest, tokenService.bearerToken)
        val rate = fxResponse.data.rates[pair]?.rate ?: BigDecimal.ONE

        log.debug(
            "Converting allocation from {} to {}: rate={}",
            sourceCurrency,
            targetCurrency,
            rate
        )

        // Convert total value
        val convertedTotalValue = allocation.totalValue.multiply(rate).setScale(2, RoundingMode.HALF_UP)

        // Convert category market values (percentages stay the same)
        val convertedBreakdown =
            allocation.categoryBreakdown.mapValues { (_, categoryAllocation) ->
                categoryAllocation.copy(
                    marketValue =
                        categoryAllocation.marketValue
                            .multiply(rate)
                            .setScale(2, RoundingMode.HALF_UP)
                )
            }

        return allocation.copy(
            totalValue = convertedTotalValue,
            currency = targetCurrency,
            categoryBreakdown = convertedBreakdown
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(AllocationService::class.java)

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