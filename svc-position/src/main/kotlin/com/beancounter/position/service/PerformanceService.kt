package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.irr.ValuationSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Calculates portfolio performance using Time-Weighted Return (TWR).
 *
 * TWR neutralizes the impact of external cash flows (deposits/withdrawals)
 * to measure pure investment performance, following GIPS standards.
 *
 * All calculations are performed in the portfolio's reference CURRENCY
 * (not the owner's base currency) using historical FX rates at each valuation date.
 *
 * Prices and FX rates are pre-fetched in bulk (2 HTTP calls total) to avoid
 * per-date round-trips that cause rate limiting.
 */
@Service
class PerformanceService(
    private val trnService: TrnService,
    private val accumulator: Accumulator,
    private val priceService: PriceService,
    private val fxRateService: FxService,
    private val twrCalculator: TwrCalculator,
    private val dateUtils: DateUtils,
    private val tokenService: TokenService,
    private val cashUtils: CashUtils = CashUtils()
) {
    fun calculate(
        portfolio: Portfolio,
        months: Int = 12
    ): PerformanceResponse {
        val token = tokenService.bearerToken
        val endDate = dateUtils.date
        val startDate = endDate.minusMonths(months.toLong())

        val transactions = fetchAndSortTransactions(portfolio)
        if (transactions.isEmpty()) return emptyResponse(portfolio)

        val valuationDates = determineValuationDates(transactions, startDate, endDate)
        if (valuationDates.isEmpty()) return emptyResponse(portfolio)

        // Collect all unique non-cash assets and currency pairs from transactions
        val allAssets = collectAssets(transactions)
        val allPairs = collectFxPairs(transactions, portfolio)

        // Pre-fetch all prices and FX rates in exactly 2 bulk calls
        val priceCache = prefetchPrices(allAssets, valuationDates, token)
        val fxCache = prefetchFxRates(allPairs, startDate, endDate, token)

        val (snapshots, netContributions, cumulativeDividends) =
            buildSnapshots(
                portfolio,
                transactions,
                valuationDates,
                priceCache,
                fxCache
            )

        return buildResponse(portfolio, snapshots, netContributions, cumulativeDividends)
    }

    private fun collectAssets(transactions: List<Trn>): List<PriceAsset> =
        transactions
            .map { it.asset }
            .filter { !cashUtils.isCash(it) }
            .distinctBy { it.id }
            .map { PriceAsset(it) }

    private fun collectFxPairs(
        transactions: List<Trn>,
        portfolio: Portfolio
    ): Set<IsoCurrencyPair> =
        transactions
            .mapNotNull { trn ->
                toPair(trn.tradeCurrency, portfolio.currency)
            }.toSet()

    private fun prefetchPrices(
        assets: List<PriceAsset>,
        dates: List<LocalDate>,
        token: String
    ): Map<String, Collection<MarketData>> {
        if (assets.isEmpty()) return emptyMap()
        val request =
            BulkPriceRequest(
                dates = dates.map { it.toString() },
                assets = assets
            )
        return priceService.getBulkPrices(request, token).data
    }

    private fun prefetchFxRates(
        pairs: Set<IsoCurrencyPair>,
        startDate: LocalDate,
        endDate: LocalDate,
        token: String
    ): Map<String, FxPairResults> {
        if (pairs.isEmpty()) return emptyMap()
        val request =
            BulkFxRequest(
                startDate = startDate.toString(),
                endDate = endDate.toString(),
                pairs = pairs
            )
        return fxRateService.getBulkRates(request, token).data
    }

    private fun fetchAndSortTransactions(portfolio: Portfolio): List<Trn> =
        trnService.query(portfolio, DateUtils.TODAY).data.sortedBy { it.tradeDate }

    /**
     * Determines when to value the portfolio: at each external cash flow date
     * plus monthly intervals, all within the requested date range.
     */
    internal fun determineValuationDates(
        transactions: List<Trn>,
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

    /**
     * Walks through transactions chronologically, accumulating positions and
     * taking valuation snapshots at each target date using pre-fetched caches.
     */
    private fun buildSnapshots(
        portfolio: Portfolio,
        transactions: List<Trn>,
        valuationDates: List<LocalDate>,
        priceCache: Map<String, Collection<MarketData>>,
        fxCache: Map<String, FxPairResults>
    ): Triple<List<ValuationSnapshot>, List<BigDecimal>, List<BigDecimal>> {
        val positions = Positions(portfolio)
        val snapshots = mutableListOf<ValuationSnapshot>()
        val netContributionsList = mutableListOf<BigDecimal>()
        val cumulativeDividendsList = mutableListOf<BigDecimal>()
        var trnIndex = 0
        var netContributions = BigDecimal.ZERO
        var cumulativeDividends = BigDecimal.ZERO

        for (valDate in valuationDates) {
            var cashFlowOnDate = BigDecimal.ZERO

            // Accumulate transactions up to and including this date
            while (trnIndex < transactions.size && !transactions[trnIndex].tradeDate.isAfter(valDate)) {
                val trn = transactions[trnIndex]
                accumulator.accumulate(trn, positions)

                if (isExternalCashFlow(trn.trnType)) {
                    val amount = convertCashFlowToPortfolioCurrency(trn, portfolio)
                    netContributions = netContributions.add(amount)
                    if (trn.tradeDate == valDate) {
                        cashFlowOnDate = cashFlowOnDate.add(amount)
                    }
                }

                if (trn.trnType == TrnType.DIVI) {
                    cumulativeDividends =
                        cumulativeDividends.add(
                            convertDividendToPortfolioCurrency(trn, portfolio)
                        )
                }
                trnIndex++
            }

            val marketValue = valuePositionsFromCache(positions, valDate, portfolio, priceCache, fxCache)

            snapshots.add(
                ValuationSnapshot(
                    date = valDate,
                    marketValue = marketValue,
                    externalCashFlow = cashFlowOnDate
                )
            )
            netContributionsList.add(netContributions)
            cumulativeDividendsList.add(cumulativeDividends)
        }

        return Triple(snapshots, netContributionsList, cumulativeDividendsList)
    }

    private fun convertDividendToPortfolioCurrency(
        trn: Trn,
        portfolio: Portfolio
    ): BigDecimal {
        val amount = trn.tradeAmount.abs()
        if (trn.tradeCurrency.code == portfolio.currency.code) return amount
        val rate = trn.tradePortfolioRate
        return if (rate.signum() != 0) {
            amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            amount
        }
    }

    /**
     * Calculates total portfolio market value at a given date in the portfolio's
     * reference currency, using pre-fetched price and FX caches.
     *
     * For each position with non-zero quantity:
     *   marketValue = quantity * price * fx_rate(trade->portfolio_currency)
     *
     * Cash positions use quantity as their value (no price lookup needed).
     */
    private fun valuePositionsFromCache(
        positions: Positions,
        date: LocalDate,
        portfolio: Portfolio,
        priceCache: Map<String, Collection<MarketData>>,
        fxCache: Map<String, FxPairResults>
    ): BigDecimal {
        val dateStr = date.toString()
        val fxRates = findNearestFxRates(dateStr, fxCache)

        val nonCashPositions =
            positions.positions.values
                .filter { it.quantityValues.getTotal().signum() != 0 }
                .filter { !cashUtils.isCash(it.asset) }

        val cashPositions =
            positions.positions.values
                .filter { it.quantityValues.getTotal().signum() != 0 }
                .filter { cashUtils.isCash(it.asset) }

        var totalMv = BigDecimal.ZERO

        // Add cash position values (quantity = value in trade currency, convert to portfolio currency)
        for (pos in cashPositions) {
            val tradeCcy = pos.moneyValues[Position.In.TRADE]?.currency ?: pos.asset.market.currency
            if (tradeCcy.code == portfolio.currency.code) {
                totalMv = totalMv.add(pos.quantityValues.getTotal())
            } else {
                val fxPair = IsoCurrencyPair(tradeCcy.code, portfolio.currency.code)
                val rate = fxRates?.rates?.get(fxPair)?.rate
                if (rate == null) {
                    log.warn("Missing FX rate for cash position {} on {}, defaulting to 1.0", fxPair, dateStr)
                }
                totalMv = totalMv.add(pos.quantityValues.getTotal().multiply(rate ?: BigDecimal.ONE))
            }
        }

        if (nonCashPositions.isEmpty()) return totalMv

        // Look up prices from pre-fetched cache
        val pricesForDate = priceCache[dateStr] ?: emptyList()
        val priceMap = pricesForDate.associateBy { "${it.asset.market.code}:${it.asset.code}" }

        for (pos in nonCashPositions) {
            val key = "${pos.asset.market.code}:${pos.asset.code}"
            val marketData = priceMap[key]
            if (marketData == null) {
                log.warn("No price data for {} on {}, skipping position", key, dateStr)
                continue
            }
            val price = marketData.close

            val tradeCcy = pos.moneyValues[Position.In.TRADE]?.currency ?: pos.asset.market.currency
            val rate =
                if (tradeCcy.code == portfolio.currency.code) {
                    BigDecimal.ONE
                } else {
                    val fxPair = IsoCurrencyPair(tradeCcy.code, portfolio.currency.code)
                    val fxRate = fxRates?.rates?.get(fxPair)?.rate
                    if (fxRate == null) {
                        log.warn("Missing FX rate for position {} on {}, defaulting to 1.0", fxPair, dateStr)
                    }
                    fxRate ?: BigDecimal.ONE
                }

            val positionMv =
                pos.quantityValues
                    .getTotal()
                    .multiply(price)
                    .multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP)
            totalMv = totalMv.add(positionMv)
        }

        return totalMv
    }

    /**
     * Finds FX rates for the exact date, or falls back to the nearest prior date in the cache.
     */
    private fun findNearestFxRates(
        dateStr: String,
        fxCache: Map<String, FxPairResults>
    ): FxPairResults? {
        fxCache[dateStr]?.let { return it }
        // Fall back to the nearest prior date
        val sortedDates = fxCache.keys.sorted()
        return sortedDates.lastOrNull { it <= dateStr }?.let { fxCache[it] }
    }

    /**
     * Converts an external cash flow amount to the portfolio's reference currency
     * using the transaction's stored tradePortfolioRate (trade → portfolio.currency).
     */
    private fun convertCashFlowToPortfolioCurrency(
        trn: Trn,
        portfolio: Portfolio
    ): BigDecimal {
        val rawAmount =
            if (trn.cashAmount.signum() != 0) {
                trn.cashAmount
            } else {
                trn.tradeAmount
            }
        // Normalize sign: deposits/income positive, withdrawals/expenses negative.
        // cashAmount sign is inconsistent across import sources, so enforce here.
        val amount = normalizeExternalCashFlow(trn.trnType, rawAmount)

        // If trade currency == portfolio currency, no conversion needed
        if (trn.tradeCurrency.code == portfolio.currency.code) return amount

        // Use stored tradePortfolioRate (trade → portfolio.currency)
        val rate = trn.tradePortfolioRate
        return if (rate.signum() != 0) {
            amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            amount
        }
    }

    /**
     * Ensures external cash flow amounts have the correct sign for TWR calculation:
     *   DEPOSIT/INCOME  → positive (money in)
     *   WITHDRAWAL/DEDUCTION/EXPENSE → negative (money out)
     */
    private fun normalizeExternalCashFlow(
        trnType: TrnType,
        amount: BigDecimal
    ): BigDecimal =
        when (trnType) {
            TrnType.DEPOSIT, TrnType.INCOME -> {
                amount.abs()
            }
            TrnType.WITHDRAWAL, TrnType.DEDUCTION, TrnType.EXPENSE -> {
                amount.abs().negate()
            }
            else -> {
                amount
            }
        }

    private fun buildResponse(
        portfolio: Portfolio,
        snapshots: List<ValuationSnapshot>,
        netContributions: List<BigDecimal>,
        cumulativeDividends: List<BigDecimal>
    ): PerformanceResponse {
        val series = twrCalculator.calculateSeries(snapshots)
        val dataPoints =
            snapshots.mapIndexed { index, snapshot ->
                val growthOf1000 = series.getOrElse(index) { BigDecimal("1000") }
                val cumulativeReturn =
                    growthOf1000
                        .divide(BigDecimal("1000"), 6, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                PerformanceDataPoint(
                    date = snapshot.date,
                    growthOf1000 = growthOf1000,
                    marketValue = snapshot.marketValue,
                    netContributions = netContributions.getOrElse(index) { BigDecimal.ZERO },
                    cumulativeReturn = cumulativeReturn,
                    cumulativeDividends = cumulativeDividends.getOrElse(index) { BigDecimal.ZERO }
                )
            }

        return PerformanceResponse(PerformanceData(portfolio.currency, dataPoints))
    }

    private fun emptyResponse(portfolio: Portfolio): PerformanceResponse =
        PerformanceResponse(PerformanceData(portfolio.currency, emptyList()))

    private fun generateMonthlyDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = startDate.withDayOfMonth(1).plusMonths(1) // First of each month after start
        while (!current.isAfter(endDate)) {
            dates.add(current)
            current = current.plusMonths(1)
        }
        return dates
    }

    companion object {
        private val log = LoggerFactory.getLogger(PerformanceService::class.java)

        /**
         * External cash flows are money entering or leaving the portfolio from outside.
         * These break TWR sub-periods. BUY/SELL are internal (cash <-> equity) and don't.
         */
        fun isExternalCashFlow(trnType: TrnType): Boolean =
            trnType == TrnType.DEPOSIT ||
                trnType == TrnType.WITHDRAWAL ||
                trnType == TrnType.INCOME ||
                trnType == TrnType.DEDUCTION ||
                trnType == TrnType.EXPENSE
    }
}