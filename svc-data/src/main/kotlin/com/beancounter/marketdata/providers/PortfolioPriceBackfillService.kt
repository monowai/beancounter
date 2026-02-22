package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Pre-populates the price database for a portfolio's historical dates.
 * This avoids slow first-time performance calculations by ensuring prices
 * exist in the DB before they are needed.
 */
@Service
class PortfolioPriceBackfillService(
    private val portfolioService: PortfolioService,
    private val trnService: TrnService,
    private val priceProcessor: MarketDataPriceProcessor,
    private val providerUtils: ProviderUtils,
    private val dateUtils: DateUtils,
    private val cashUtils: CashUtils
) {
    private val log = LoggerFactory.getLogger(PortfolioPriceBackfillService::class.java)

    fun backfill(code: String): Map<String, Any> {
        val portfolio = portfolioService.findByCode(code)
        val today = dateUtils.date
        val transactions =
            trnService
                .findForPortfolio(portfolio, today)
                .sortedBy { it.tradeDate }

        if (transactions.isEmpty()) {
            return mapOf("portfolio" to code, "status" to "no_transactions")
        }

        val assets =
            transactions
                .map { it.asset }
                .filter { !cashUtils.isCash(it) }
                .distinctBy { it.id }

        if (assets.isEmpty()) {
            return mapOf("portfolio" to code, "status" to "no_assets")
        }

        val startDate = transactions.first().tradeDate
        val valuationDates = determineValuationDates(transactions, startDate, today)
        val priceAssets = providerUtils.getInputs(assets)

        log.info(
            "Backfilling prices for portfolio {}: {} dates, {} assets",
            code,
            valuationDates.size,
            assets.size
        )

        var pricesLoaded = 0
        for (date in valuationDates) {
            val request = PriceRequest(date.toString(), priceAssets, currentMode = false)
            val response = priceProcessor.getPriceResponse(request)
            pricesLoaded += response.data.size
        }

        log.info("Backfill complete for portfolio {}: {} prices loaded", code, pricesLoaded)

        return mapOf(
            "status" to "ok",
            "portfolio" to code,
            "datesProcessed" to valuationDates.size,
            "assetsProcessed" to assets.size,
            "pricesLoaded" to pricesLoaded
        )
    }

    internal fun determineValuationDates(
        transactions: Collection<com.beancounter.common.model.Trn>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        val cashFlowDates =
            transactions
                .filter { isExternalCashFlow(it.trnType) }
                .filter { !it.tradeDate.isBefore(startDate) && !it.tradeDate.isAfter(endDate) }
                .map { it.tradeDate }

        val monthlyDates = generateMonthlyDates(startDate, endDate)

        return (cashFlowDates + monthlyDates + listOf(startDate, endDate))
            .filter { !it.isBefore(startDate) && !it.isAfter(endDate) }
            .distinct()
            .sorted()
    }

    private fun generateMonthlyDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = startDate.withDayOfMonth(1).plusMonths(1)
        while (!current.isAfter(endDate)) {
            dates.add(current)
            current = current.plusMonths(1)
        }
        return dates
    }

    companion object {
        fun isExternalCashFlow(trnType: TrnType): Boolean =
            trnType == TrnType.DEPOSIT ||
                trnType == TrnType.WITHDRAWAL ||
                trnType == TrnType.INCOME ||
                trnType == TrnType.DEDUCTION ||
                trnType == TrnType.EXPENSE
    }
}