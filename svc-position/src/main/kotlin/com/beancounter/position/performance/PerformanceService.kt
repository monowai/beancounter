package com.beancounter.position.performance

import com.beancounter.common.contracts.AggregatedPerformanceData
import com.beancounter.common.contracts.AggregatedPerformanceResponse
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.irr.ValuationSnapshot
import com.beancounter.position.performance.cache.CachedSnapshot
import com.beancounter.position.performance.cache.PerformanceSnapshotCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Calculates portfolio performance using Time-Weighted Return (TWR).
 *
 * TWR neutralizes the impact of external cash flows (deposits/withdrawals)
 * to measure pure investment performance, following GIPS standards; the
 * [TwrBoundary] decides what counts as one.
 *
 * All calculations are performed in the portfolio's reference CURRENCY
 * (not the owner's base currency) using historical FX rates at each valuation date.
 *
 * Prices and FX rates are pre-fetched in bulk by [PerformanceInputs] (2 HTTP calls
 * total) to avoid per-date round-trips that cause rate limiting.
 */
@Service
class PerformanceService(
    private val inputs: PerformanceInputs,
    private val replayer: SnapshotReplayer,
    private val snapshotCache: PerformanceSnapshotCache,
    private val twrCalculator: TwrCalculator,
    private val aggregateComposer: AggregatePerformanceComposer,
    private val dateUtils: DateUtils
) {
    fun calculate(
        portfolio: Portfolio,
        months: Int = 12
    ): PerformanceResponse = calculate(portfolio, months, historyAssetIds = null)

    /**
     * [historyAssetIds] collects the assets to nudge for price history instead of
     * nudging here, so [aggregate] can send one request for every portfolio. Null
     * nudges at once.
     */
    private fun calculate(
        portfolio: Portfolio,
        months: Int,
        historyAssetIds: MutableSet<String>?
    ): PerformanceResponse {
        val endDate = dateUtils.date
        val startDate = endDate.minusMonths(months.toLong())

        // Check cache BEFORE fetching transactions to skip all HTTP calls on hit
        val cached = snapshotCache.window(portfolio, startDate, endDate)
        return if (cached != null) {
            response(portfolio, cached)
        } else {
            recompute(portfolio, startDate, endDate, historyAssetIds)
        }
    }

    /** Values [portfolio] from its transactions, or an empty response when there is nothing to value. */
    private fun recompute(
        portfolio: Portfolio,
        startDate: LocalDate,
        endDate: LocalDate,
        historyAssetIds: MutableSet<String>?
    ): PerformanceResponse {
        val transactions = inputs.transactions(portfolio)
        val valuationDates =
            if (transactions.isEmpty()) emptyList() else replayer.valuationDates(transactions, startDate, endDate)
        if (valuationDates.isEmpty()) {
            return PerformanceResponse(PerformanceData(portfolio.currency, emptyList()))
        }
        log.debug("Cache MISS: portfolio={}, dates={}", portfolio.code, valuationDates.size)

        val prices = inputs.prices(portfolio, transactions, valuationDates, historyAssetIds)
        val snapshots = replayer.replay(portfolio, transactions, valuationDates, prices)
        snapshotCache.store(portfolio.id, snapshots)
        return response(portfolio, snapshots, transactions.first().tradeDate)
    }

    private fun response(
        portfolio: Portfolio,
        snapshots: List<CachedSnapshot>,
        firstTradeDate: LocalDate? = null
    ): PerformanceResponse {
        val series =
            twrCalculator.calculateSeries(
                snapshots.map { ValuationSnapshot(it.valuationDate, it.marketValue, it.externalCashFlow) }
            )
        val dataPoints =
            snapshots.mapIndexed { index, snapshot ->
                val growthOf1000 = series.getOrElse(index) { TwrCalculator.GROWTH_BASE }
                PerformanceDataPoint(
                    date = snapshot.valuationDate,
                    growthOf1000 = growthOf1000,
                    marketValue = snapshot.marketValue,
                    netContributions = snapshot.netContributions,
                    cumulativeReturn =
                        growthOf1000
                            .divide(TwrCalculator.GROWTH_BASE, CUMULATIVE_RETURN_SCALE, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE),
                    cumulativeDividends = snapshot.cumulativeDividends
                )
            }
        return PerformanceResponse(PerformanceData(portfolio.currency, dataPoints, firstTradeDate))
    }

    /**
     * Aggregate per-portfolio TWR series into a single composite series in
     * `displayCurrency`. Uses each portfolio's existing cached / computed TWR
     * (`calculate()`) and combines with chained sub-period AUM-weighted
     * composite per GIPS; see [AggregatePerformanceComposer].
     */
    fun aggregate(
        portfolios: List<Portfolio>,
        months: Int,
        displayCurrency: Currency
    ): AggregatedPerformanceResponse {
        if (portfolios.isEmpty()) {
            return AggregatedPerformanceResponse(AggregatedPerformanceData(displayCurrency))
        }
        val perPortfolio = calculateAll(portfolios, months)
        val fxRates = inputs.displayCurrencyRates(perPortfolio, displayCurrency, dateUtils.date)
        return aggregateComposer.compose(perPortfolio, displayCurrency, fxRates)
    }

    /**
     * Calculates each portfolio, [AGGREGATE_CONCURRENCY] at a time, then nudges price
     * history once for the assets of every portfolio that missed the cache. One at a
     * time, a cold 18-portfolio wealth page ran over 100s on kauri and hit the 60s
     * bulk-price read timeout.
     */
    private fun calculateAll(
        portfolios: List<Portfolio>,
        months: Int
    ): List<Pair<Portfolio, PerformanceData>> {
        val securityContext = SecurityContextHolder.getContext()
        val historyAssetIds = ConcurrentHashMap.newKeySet<String>()
        val permits = Semaphore(AGGREGATE_CONCURRENCY)
        val perPortfolio =
            runBlockingTraced(Dispatchers.IO) {
                portfolios
                    .map { portfolio ->
                        async {
                            permits.withPermit {
                                SecurityContextHolder.setContext(securityContext)
                                try {
                                    portfolio to calculate(portfolio, months, historyAssetIds).data
                                } finally {
                                    SecurityContextHolder.clearContext()
                                }
                            }
                        }
                    }.awaitAll()
            }
        inputs.ensureAssetHistory(historyAssetIds, dateUtils.date.minusMonths(months.toLong()))
        return perPortfolio
    }

    companion object {
        private val log = LoggerFactory.getLogger(PerformanceService::class.java)

        private const val CUMULATIVE_RETURN_SCALE = 6

        // Portfolios calculated at once by [aggregate]. Each cold calculation makes bulk
        // price and FX calls to bc-data, whose 512m heap is the constraint.
        private const val AGGREGATE_CONCURRENCY = 4
    }
}