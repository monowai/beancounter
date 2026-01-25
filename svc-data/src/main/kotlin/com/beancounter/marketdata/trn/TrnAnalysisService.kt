package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.classification.ClassificationService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Service for transaction analysis operations including investment tracking and income reporting.
 */
@Service
@Transactional
class TrnAnalysisService(
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val fxRateService: FxRateService,
    private val assetRepository: AssetRepository,
    private val classificationService: ClassificationService
) {
    private val log = LoggerFactory.getLogger(TrnAnalysisService::class.java)

    /**
     * Get the total investment amount for the current user in a specific month.
     * Sums all BUY and ADD transactions across all portfolios.
     *
     * @param yearMonth The month to calculate (e.g., YearMonth.now() for current month)
     * @return Total investment amount for the month
     */
    fun getMonthlyInvestment(yearMonth: YearMonth): BigDecimal {
        val user = systemUserService.getOrThrow()
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val total =
            trnRepository.sumInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )
        log.trace("Monthly investment for $yearMonth: $total")
        return total
    }

    /**
     * Get all investment transactions for the current user in a specific month.
     * Returns individual BUY and ADD transactions across all portfolios.
     *
     * @param yearMonth The month to query
     * @return Collection of investment transactions
     */
    fun getMonthlyInvestmentTransactions(yearMonth: YearMonth): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val results =
            trnRepository.findInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )
        log.trace("Monthly investment trns: ${results.size} in $yearMonth")
        return results.toList()
    }

    /**
     * Get net investment for specific portfolios in a month, converted to target currency.
     * Calculates: BUY + ADD - SELL with FX conversion.
     *
     * @param yearMonth The month to calculate
     * @param portfolioIds List of portfolio IDs to include (empty = all user's portfolios)
     * @param targetCurrency Currency code to convert amounts to
     * @return Net investment amount in target currency
     */
    fun getMonthlyInvestmentConverted(
        yearMonth: YearMonth,
        portfolioIds: List<String>,
        targetCurrency: String
    ): BigDecimal {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        // Fetch transactions for the specified portfolios
        val transactions =
            if (portfolioIds.isEmpty()) {
                val user = systemUserService.getOrThrow()
                trnRepository.findInvestmentsByOwnerAndDateRange(
                    user,
                    startDate,
                    endDate,
                    TrnStatus.SETTLED
                )
            } else {
                trnRepository.findInvestmentsByPortfoliosAndDateRange(
                    portfolioIds,
                    startDate,
                    endDate,
                    TrnStatus.SETTLED
                )
            }

        if (transactions.isEmpty()) {
            return BigDecimal.ZERO
        }

        val fxRates = getFxRates(transactions, targetCurrency)

        // Sum with conversion
        var total = BigDecimal.ZERO
        for (trn in transactions) {
            val convertedAmount = convertAmount(trn, targetCurrency, fxRates)

            // BUY is positive, SELL is negative (ADD excluded - represents transfers)
            total =
                when (trn.trnType) {
                    TrnType.BUY -> total.add(convertedAmount)
                    TrnType.SELL -> total.subtract(convertedAmount)
                    else -> total
                }
        }

        log.trace("Monthly investment for $yearMonth in $targetCurrency: $total (${transactions.size} trns)")
        return total
    }

    /**
     * Get transaction summary for a rolling N-week window.
     * Returns total purchases, total sales, and net investment with FX conversion.
     *
     * @param weeks Number of weeks to include in the summary
     * @param targetCurrency Currency code to convert amounts to
     * @return TransactionSummary with purchases, sales, net investment, and period dates
     */
    fun getTransactionSummary(
        weeks: Int,
        targetCurrency: String
    ): TransactionSummary {
        val endDate = LocalDate.now()
        val startDate = endDate.minusWeeks(weeks.toLong())

        val user = systemUserService.getOrThrow()
        val transactions =
            trnRepository.findInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )

        if (transactions.isEmpty()) {
            return TransactionSummary(
                totalPurchases = BigDecimal.ZERO,
                totalSales = BigDecimal.ZERO,
                netInvestment = BigDecimal.ZERO,
                periodStart = startDate,
                periodEnd = endDate,
                currency = targetCurrency
            )
        }

        val fxRates = getFxRates(transactions, targetCurrency)

        // Sum purchases and sales separately with conversion
        var totalPurchases = BigDecimal.ZERO
        var totalSales = BigDecimal.ZERO

        for (trn in transactions) {
            val convertedAmount = convertAmount(trn, targetCurrency, fxRates)

            when (trn.trnType) {
                TrnType.BUY -> {
                    totalPurchases = totalPurchases.add(convertedAmount)
                }
                TrnType.SELL -> {
                    totalSales = totalSales.add(convertedAmount)
                }
                else -> { // ignore other types
                }
            }
        }

        val netInvestment = totalPurchases.subtract(totalSales)

        log.trace(
            "Transaction summary for {} weeks in {}: purchases={}, sales={}, net={} ({} trns)",
            weeks,
            targetCurrency,
            totalPurchases,
            totalSales,
            netInvestment,
            transactions.size
        )

        return TransactionSummary(
            totalPurchases = totalPurchases,
            totalSales = totalSales,
            netInvestment = netInvestment,
            periodStart = startDate,
            periodEnd = endDate,
            currency = targetCurrency
        )
    }

    /**
     * Get monthly income data for the current user over a rolling period.
     *
     * @param months Number of months to include (default 12)
     * @param endMonth End month (default current month)
     * @param portfolioIds Optional list of portfolio IDs to filter by
     * @param groupBy Grouping option: "assetClass", "sector", "currency", or "market"
     * @return MonthlyIncomeResponse with aggregated income data
     */
    fun getMonthlyIncome(
        months: Int = 12,
        endMonth: YearMonth = YearMonth.now(),
        portfolioIds: List<String> = emptyList(),
        groupBy: String = "assetClass"
    ): MonthlyIncomeResponse {
        val startMonth = endMonth.minusMonths((months - 1).toLong())
        val startDate = startMonth.atDay(1)
        val endDate = endMonth.atEndOfMonth()

        // Fetch transactions (without full asset hydration)
        val transactions =
            if (portfolioIds.isEmpty()) {
                val user = systemUserService.getOrThrow()
                trnRepository.findIncomeByOwnerAndDateRange(
                    user,
                    startDate,
                    endDate,
                    TrnStatus.SETTLED
                )
            } else {
                trnRepository.findIncomeByPortfoliosAndDateRange(
                    portfolioIds,
                    startDate,
                    endDate,
                    TrnStatus.SETTLED
                )
            }

        // Build asset metadata lookup efficiently
        val assetMetaData = buildAssetMetaData(transactions.toList(), groupBy)

        // Generate all months in range
        val allMonths = mutableListOf<YearMonth>()
        var current = startMonth
        while (!current.isAfter(endMonth)) {
            allMonths.add(current)
            current = current.plusMonths(1)
        }

        // Aggregate by month
        val monthlyTotals = mutableMapOf<YearMonth, BigDecimal>()
        allMonths.forEach { monthlyTotals[it] = BigDecimal.ZERO }

        // Generic aggregation structure
        data class GroupAggregation(
            val groupKey: String,
            var totalIncome: BigDecimal = BigDecimal.ZERO,
            val monthlyData: MutableList<MonthlyIncomeData>,
            val assetIncomes: MutableMap<String, IncomeContributor> = mutableMapOf()
        )

        val groupData = mutableMapOf<String, GroupAggregation>()

        for (trn in transactions) {
            val trnMonth = YearMonth.from(trn.tradeDate)
            val amount = trn.tradeAmount
            val monthIndex = allMonths.indexOf(trnMonth)

            // Update monthly total
            monthlyTotals[trnMonth] = monthlyTotals.getOrDefault(trnMonth, BigDecimal.ZERO).add(amount)

            val assetId = trn.asset.id
            val meta = assetMetaData[assetId]

            // Determine group key based on groupBy parameter using metadata
            val groupKey =
                when (groupBy) {
                    "assetClass" -> meta?.category?.ifBlank { "Unknown" } ?: "Unknown"
                    "sector" -> meta?.sector?.ifBlank { "Unknown" } ?: "Unknown"
                    "currency" -> trn.tradeCurrency.code
                    "market" -> meta?.marketCode ?: "Unknown"
                    else -> meta?.category?.ifBlank { "Unknown" } ?: "Unknown"
                }

            // Update group data
            val groupAgg =
                groupData.getOrPut(groupKey) {
                    GroupAggregation(
                        groupKey = groupKey,
                        monthlyData =
                            allMonths
                                .map {
                                    MonthlyIncomeData(
                                        it.toString(),
                                        BigDecimal.ZERO
                                    )
                                }.toMutableList()
                    )
                }
            groupAgg.totalIncome = groupAgg.totalIncome.add(amount)
            if (monthIndex >= 0) {
                val existingData = groupAgg.monthlyData[monthIndex]
                groupAgg.monthlyData[monthIndex] =
                    MonthlyIncomeData(existingData.yearMonth, existingData.income.add(amount))
            }
            // Track asset contribution within group using metadata
            val assetContrib =
                groupAgg.assetIncomes.getOrPut(assetId) {
                    IncomeContributor(assetId, meta?.code ?: assetId, meta?.name, BigDecimal.ZERO)
                }
            assetContrib.totalIncome = assetContrib.totalIncome.add(amount)
        }

        val totalIncome = monthlyTotals.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        val monthlyDataList = allMonths.map { MonthlyIncomeData(it.toString(), monthlyTotals[it] ?: BigDecimal.ZERO) }

        // Convert aggregations to response format with top 10 contributors
        val groupIncomeList =
            groupData.values
                .map { agg ->
                    IncomeGroupData(
                        groupKey = agg.groupKey,
                        totalIncome = agg.totalIncome,
                        monthlyData = agg.monthlyData,
                        topContributors =
                            agg.assetIncomes.values
                                .sortedByDescending { it.totalIncome }
                                .take(10)
                    )
                }.sortedByDescending { it.totalIncome }

        return MonthlyIncomeResponse(
            startMonth = startMonth.toString(),
            endMonth = endMonth.toString(),
            totalIncome = totalIncome,
            groupBy = groupBy,
            months = monthlyDataList,
            groups = groupIncomeList
        )
    }

    /**
     * Build a lightweight asset metadata lookup for income reporting.
     * Fetches only the fields needed for grouping without full asset hydration.
     */
    private fun buildAssetMetaData(
        transactions: List<Trn>,
        groupBy: String
    ): Map<String, AssetMetaData> {
        if (transactions.isEmpty()) return emptyMap()

        // Collect distinct asset IDs
        val assetIds = transactions.map { it.asset.id }.distinct()
        log.trace("Building metadata for ${assetIds.size} distinct assets")

        // Fetch basic asset info (category, marketCode, code, name)
        val assets = assetRepository.findAllById(assetIds).associateBy { it.id }

        // Fetch sector data only if grouping by sector
        val sectorData =
            if (groupBy == "sector") {
                classificationService.getClassificationSummaries(assetIds)
            } else {
                emptyMap()
            }

        // Build metadata map
        return assetIds.associateWith { assetId ->
            val asset = assets[assetId]
            val classification = sectorData[assetId]
            AssetMetaData(
                assetId = assetId,
                code = asset?.code ?: assetId,
                name = asset?.name,
                category = asset?.category ?: "Unknown",
                marketCode = asset?.marketCode ?: "Unknown",
                sector = classification?.sector
            )
        }
    }

    /**
     * Get FX rates for converting transaction amounts to target currency.
     */
    private fun getFxRates(
        transactions: Iterable<Trn>,
        targetCurrency: String
    ): Map<String, BigDecimal> {
        val currenciesNeeded =
            transactions
                .map { it.tradeCurrency.code }
                .filter { it != targetCurrency }
                .distinct()

        return if (currenciesNeeded.isNotEmpty()) {
            val fxRequest = FxRequest(rateDate = "today")
            currenciesNeeded.forEach { fxRequest.add(IsoCurrencyPair(it, targetCurrency)) }
            val fxResponse = fxRateService.getRates(fxRequest, "")
            fxResponse.data.rates.entries.associate { (pair, fxRate) ->
                "${pair.from}:${pair.to}" to fxRate.rate
            }
        } else {
            emptyMap()
        }
    }

    /**
     * Convert a transaction amount to the target currency.
     */
    private fun convertAmount(
        trn: Trn,
        targetCurrency: String,
        fxRates: Map<String, BigDecimal>
    ): BigDecimal {
        val amount = trn.tradeAmount
        val fromCurrency = trn.tradeCurrency.code

        return if (fromCurrency == targetCurrency) {
            amount
        } else {
            val rateKey = "$fromCurrency:$targetCurrency"
            val rate = fxRates[rateKey] ?: BigDecimal.ONE
            amount.multiply(rate)
        }
    }
}