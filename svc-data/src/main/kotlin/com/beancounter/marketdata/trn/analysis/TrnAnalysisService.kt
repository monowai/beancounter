package com.beancounter.marketdata.trn.analysis

import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TransactionSummary
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Investment tracking: net BUY - SELL activity for the current user. Income reporting lives in
 * [IncomeAnalysisService].
 */
@Service
@Transactional
class TrnAnalysisService(
    private val trnRepository: TrnRepository,
    private val systemUserService: SystemUserService,
    private val portfolioService: PortfolioService,
    private val tradeAmountFx: TradeAmountFx,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(TrnAnalysisService::class.java)

    /**
     * Get the net investment amount for the current user over a date range.
     * Sums BUY - SELL (ADD excluded as transfers) across all portfolios.
     *
     * @param startDate window start (inclusive)
     * @param endDate window end (inclusive)
     * @return Total net investment for the window
     */
    fun getMonthlyInvestment(
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal {
        val user = systemUserService.getOrThrow()
        val total =
            trnRepository.sumInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )
        log.trace("Investment for $startDate..$endDate: $total")
        return total
    }

    /**
     * Get all investment transactions for the current user over a date range.
     * Returns individual BUY and SELL transactions across all portfolios.
     *
     * @param startDate window start (inclusive)
     * @param endDate window end (inclusive)
     * @return Collection of investment transactions
     */
    fun getMonthlyInvestmentTransactions(
        startDate: LocalDate,
        endDate: LocalDate
    ): Collection<Trn> {
        val user = systemUserService.getOrThrow()
        val results =
            trnRepository.findInvestmentsByOwnerAndDateRange(
                user,
                startDate,
                endDate,
                TrnStatus.SETTLED
            )
        log.trace("Investment trns: ${results.size} in $startDate..$endDate")
        return results.toList()
    }

    /**
     * Get net investment for specific portfolios over a date range, converted to target currency.
     * Calculates: BUY - SELL with FX conversion.
     *
     * @param startDate window start (inclusive)
     * @param endDate window end (inclusive)
     * @param portfolioIds List of portfolio IDs to include (empty = all user's portfolios)
     * @param targetCurrency Currency code to convert amounts to
     * @return Net investment amount in target currency
     */
    fun getMonthlyInvestmentConverted(
        startDate: LocalDate,
        endDate: LocalDate,
        portfolioIds: List<String>,
        targetCurrency: String
    ): BigDecimal {
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
                requireAccess(portfolioIds)
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

        val amounts = tradeAmountFx.into(targetCurrency, transactions)

        var total = BigDecimal.ZERO
        for (trn in transactions) {
            val convertedAmount = amounts.of(trn)

            // BUY is positive, SELL is negative (ADD excluded - represents transfers)
            total =
                when (trn.trnType) {
                    TrnType.BUY -> total.add(convertedAmount)
                    TrnType.SELL -> total.subtract(convertedAmount)
                    else -> total
                }
        }

        log.trace("Investment for $startDate..$endDate in $targetCurrency: $total (${transactions.size} trns)")
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
        val endDate = dateUtils.date
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

        val amounts = tradeAmountFx.into(targetCurrency, transactions)

        var totalPurchases = BigDecimal.ZERO
        var totalSales = BigDecimal.ZERO

        for (trn in transactions) {
            val convertedAmount = amounts.of(trn)

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
     * [PortfolioService.find] per id, so an unknown or unauthorized portfolio
     * rejects the whole request rather than returning a partial report.
     */
    private fun requireAccess(portfolioIds: List<String>) {
        portfolioIds.forEach(portfolioService::find)
    }
}