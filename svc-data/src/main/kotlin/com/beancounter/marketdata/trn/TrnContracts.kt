package com.beancounter.marketdata.trn

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request to settle proposed transactions.
 */
data class SettleTransactionsRequest(
    val trnIds: List<String>
)

/**
 * Individual transaction in broker holdings report.
 */
data class BrokerHoldingTransaction(
    val id: String,
    val portfolioId: String,
    val portfolioCode: String,
    val tradeDate: String,
    val trnType: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val tradeAmount: BigDecimal
)

data class BrokerPortfolioGroup(
    val portfolioId: String,
    val portfolioCode: String,
    val quantity: BigDecimal,
    val transactions: List<BrokerHoldingTransaction>
)

data class BrokerHoldingPosition(
    val assetId: String,
    val assetCode: String,
    val assetName: String?,
    val market: String,
    val quantity: BigDecimal,
    val portfolioGroups: List<BrokerPortfolioGroup>
)

data class BrokerHoldingsResponse(
    val brokerId: String,
    val brokerName: String,
    val holdings: List<BrokerHoldingPosition>
)

/**
 * Response for monthly investment summary.
 */
data class MonthlyInvestmentResponse(
    val yearMonth: String,
    val totalInvested: BigDecimal,
    val currency: String? = null
)

/**
 * Transaction summary for a rolling time period.
 */
data class TransactionSummary(
    val totalPurchases: BigDecimal,
    val totalSales: BigDecimal,
    val netInvestment: BigDecimal,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currency: String
)

data class TransactionSummaryResponse(
    val data: TransactionSummary
)

/**
 * Response for monthly income report.
 */
data class MonthlyIncomeResponse(
    val startMonth: String,
    val endMonth: String,
    val totalIncome: BigDecimal,
    val groupBy: String,
    val months: List<MonthlyIncomeData>,
    val groups: List<IncomeGroupData>
)

data class MonthlyIncomeData(
    val yearMonth: String,
    val income: BigDecimal
)

data class IncomeContributor(
    val assetId: String,
    val assetCode: String,
    val assetName: String?,
    var totalIncome: BigDecimal
)

data class IncomeGroupData(
    val groupKey: String,
    val totalIncome: BigDecimal,
    val monthlyData: List<MonthlyIncomeData>,
    val topContributors: List<IncomeContributor>
)

/**
 * Lightweight asset metadata for income reporting.
 * Contains only the fields needed for grouping and display.
 */
data class AssetMetaData(
    val assetId: String,
    val code: String,
    val name: String?,
    val category: String,
    val marketCode: String,
    val sector: String?
)