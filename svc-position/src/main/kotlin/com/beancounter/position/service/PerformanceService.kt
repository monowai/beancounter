package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.AggregatedPerformanceData
import com.beancounter.common.contracts.AggregatedPerformanceDataPoint
import com.beancounter.common.contracts.AggregatedPerformanceResponse
import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.EnsureHistoryRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Currency
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
import com.beancounter.position.cache.CachedSnapshot
import com.beancounter.position.cache.PerformanceCacheService
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.irr.ValuationSnapshot
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.Executor

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
    private val irrCalculator: com.beancounter.position.irr.IrrCalculator,
    private val dateUtils: DateUtils,
    private val tokenService: TokenService,
    private val cacheService: PerformanceCacheService,
    @Qualifier("performanceNudgeExecutor")
    private val nudgeExecutor: Executor = Executor(Runnable::run),
    private val cashUtils: CashUtils = CashUtils()
) {
    fun calculate(
        portfolio: Portfolio,
        months: Int = 12
    ): PerformanceResponse {
        val endDate = dateUtils.date
        val startDate = endDate.minusMonths(months.toLong())

        // Check cache BEFORE fetching transactions to skip all HTTP calls on hit
        val cached = tryLoadFromCache(portfolio.id)
        if (cached != null) {
            val earliestCached = cached.minOf { it.valuationDate }
            val latestCached = cached.maxOf { it.valuationDate }
            val hasInWindow = cached.any { !it.valuationDate.isBefore(startDate) }
            // `earliestCached <= startDate` alone is not enough: if every cached
            // snapshot predates startDate, `anchorToStartDate` returns just the
            // synthesised anchor (single-point series), producing 0% TWR and 0
            // gain. Require at least one snapshot in `[startDate, endDate]`.
            val hasSufficientHistory = !earliestCached.isAfter(startDate) && hasInWindow
            // RabbitMQ invalidation (CacheInvalidationConsumer.invalidateFrom) deletes
            // snapshots >= a trn's tradeDate; the surviving older rows can still
            // satisfy `hasSufficientHistory`, leaving the series frozen before that
            // date forever. Require the newest cached snapshot to reach endDate too.
            // determineValuationDates always includes endDate in its result (it's
            // unconditionally unioned in before the range filter), and buildSnapshots
            // emits one snapshot per valuation date, so a fresh cache always has a
            // snapshot dated endDate.
            val isFresh = !latestCached.isBefore(endDate)
            if (hasSufficientHistory && isFresh) {
                val anchored = anchorToStartDate(cached, startDate)
                log.debug(
                    "Cache HIT: portfolio={}, snapshots={} (filtered from {})",
                    portfolio.code,
                    anchored.size,
                    cached.size
                )
                return buildResponseFromCache(portfolio, anchored)
            }
            log.debug(
                "Cache PARTIAL: portfolio={}, earliest={}, latest={}, requested from {} to {}, reason={}, recomputing",
                portfolio.code,
                earliestCached,
                latestCached,
                startDate,
                endDate,
                if (!hasSufficientHistory) "insufficient history" else "stale tail"
            )
        }

        val token = tokenService.bearerToken

        val transactions = fetchAndSortTransactions(portfolio)
        if (transactions.isEmpty()) return emptyResponse(portfolio)

        val firstTradeDate = transactions.firstOrNull()?.tradeDate

        val valuationDates = determineValuationDates(transactions, startDate, endDate)
        if (valuationDates.isEmpty()) return emptyResponse(portfolio)

        log.debug("Cache MISS: portfolio={}, dates={}", portfolio.code, valuationDates.size)

        // Collect all unique non-cash assets and currency pairs from transactions
        val allAssets = collectAssets(transactions)
        val allPairs = collectFxPairs(transactions, portfolio)

        // Fire-and-forget — nudge svc-data to backfill deep history for the
        // assets we're about to value. This lets long-window growth / wealth
        // charts converge to a complete series across requests without
        // blocking the current calculation on a multi-year provider call.
        ensureAssetHistory(allAssets, startDate, token)

        // Pre-fetch all prices and FX rates in exactly 2 bulk calls
        val priceCache = prefetchPrices(allAssets, valuationDates, token)
        val fxCache = prefetchFxRates(allPairs, valuationDates, startDate, endDate, token)

        val (snapshots, netContributions, cumulativeDividends) =
            buildSnapshots(
                portfolio,
                transactions,
                valuationDates,
                priceCache,
                fxCache
            )

        // Store computed snapshots in cache
        tryCacheSnapshots(portfolio.id, snapshots, netContributions, cumulativeDividends)

        return buildResponse(portfolio, snapshots, netContributions, cumulativeDividends, firstTradeDate)
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

    private fun ensureAssetHistory(
        assets: List<PriceAsset>,
        startDate: LocalDate,
        token: String
    ) {
        if (assets.isEmpty()) return
        val assetIds =
            assets
                .mapNotNull {
                    it.resolvedAsset?.id ?: it.assetId.takeIf { id ->
                        id.isNotEmpty()
                    }
                }.distinct()
        if (assetIds.isEmpty()) return
        // Truly fire-and-forget: dispatch the HTTP call onto a dedicated executor so
        // the request thread doesn't wait on the round-trip to svc-data. svc-data
        // returns immediately (it only schedules the backfill), but even the bare
        // RPC adds latency we don't want on every calculate().
        nudgeExecutor.execute {
            try {
                priceService.ensureHistory(
                    EnsureHistoryRequest(assetIds = assetIds, fromDate = startDate),
                    token
                )
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                // Non-fatal — performance calc continues with whatever is in the DB.
                log.warn("ensureHistory call failed (continuing)", e)
            }
        }
    }

    private fun prefetchFxRates(
        pairs: Set<IsoCurrencyPair>,
        valuationDates: List<LocalDate>,
        startDate: LocalDate,
        endDate: LocalDate,
        token: String
    ): Map<String, FxPairResults> {
        if (pairs.isEmpty()) return emptyMap()
        val request =
            BulkFxRequest(
                startDate = startDate.toString(),
                endDate = endDate.toString(),
                pairs = pairs,
                // Send the exact valuation dates so svc-data narrows its DB load to
                // those + a small lookback. Sending only [startDate, endDate] would
                // eager-load every cached FxRate in range (10y for the "ALL" wealth
                // chart) and OOM bc-data — DATA-45, 2026-05-18.
                dates = valuationDates.map { it.toString() }
            )
        return fxRateService.getBulkRates(request, token).data
    }

    private fun fetchAndSortTransactions(portfolio: Portfolio): List<Trn> =
        trnService
            .query(portfolio, DateUtils.TODAY)
            .data
            .toTrns()
            .sortedBy { it.tradeDate }

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
        // Computed once per pass (not per date) — PRIVATE-market assets only
        // ever get one price row (see valuePositionsFromCache), so every other
        // valuation date needs this fallback.
        val latestPriceIndex = buildLatestPriceIndex(priceCache)

        for (valDate in valuationDates) {
            // Accumulate transactions up to and including this date
            val acc =
                accumulateUpTo(
                    valDate,
                    transactions,
                    positions,
                    portfolio,
                    trnIndex,
                    netContributions,
                    cumulativeDividends
                )
            trnIndex = acc.trnIndex
            netContributions = acc.netContributions
            cumulativeDividends = acc.cumulativeDividends

            val marketValue =
                valuePositionsFromCache(positions, valDate, portfolio, priceCache, fxCache, latestPriceIndex)

            snapshots.add(
                ValuationSnapshot(
                    date = valDate,
                    marketValue = marketValue,
                    externalCashFlow = acc.cashFlowOnDate
                )
            )
            netContributionsList.add(netContributions)
            cumulativeDividendsList.add(cumulativeDividends)
        }

        return Triple(snapshots, netContributionsList, cumulativeDividendsList)
    }

    /** Running totals after accumulating all transactions up to a valuation date. */
    private data class DateAccumulation(
        val trnIndex: Int,
        val netContributions: BigDecimal,
        val cumulativeDividends: BigDecimal,
        val cashFlowOnDate: BigDecimal
    )

    /**
     * Accumulates transactions into [positions] up to and including [valDate],
     * advancing from [startIndex] and folding contributions/dividends onto the
     * running totals. Mutates [positions]; returns the updated scalar totals.
     */
    private fun accumulateUpTo(
        valDate: LocalDate,
        transactions: List<Trn>,
        positions: Positions,
        portfolio: Portfolio,
        startIndex: Int,
        startNetContributions: BigDecimal,
        startCumulativeDividends: BigDecimal
    ): DateAccumulation {
        var trnIndex = startIndex
        var netContributions = startNetContributions
        var cumulativeDividends = startCumulativeDividends
        var cashFlowOnDate = BigDecimal.ZERO

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
                    cumulativeDividends.add(convertDividendToPortfolioCurrency(trn, portfolio))
            }
            trnIndex++
        }

        return DateAccumulation(trnIndex, netContributions, cumulativeDividends, cashFlowOnDate)
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
     * Indexes the most recent [MarketData] per asset across every date present
     * in [priceCache]. PRIVATE-market assets (off-market property, etc.) are
     * stamped by PrivateMarketDataProvider with a single latest-price row and
     * no daily history, so the per-date lookup in [valuePositionsFromCache]
     * only ever finds a row on that one date — every other valuation date
     * needs this fallback instead of purchase cost.
     */
    private fun buildLatestPriceIndex(priceCache: Map<String, Collection<MarketData>>): Map<String, MarketData> {
        val latest = mutableMapOf<String, MarketData>()
        for (marketDataForDate in priceCache.values) {
            for (marketData in marketDataForDate) {
                val key = "${marketData.asset.market.code}:${marketData.asset.code}"
                val existing = latest[key]
                if (existing == null || marketData.priceDate.isAfter(existing.priceDate)) {
                    latest[key] = marketData
                }
            }
        }
        return latest
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
        fxCache: Map<String, FxPairResults>,
        latestPriceIndex: Map<String, MarketData>
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
            val price =
                if (marketData != null) {
                    marketData.close
                } else if (pos.asset.market.code == PRIVATE_MARKET && latestPriceIndex[key] != null) {
                    // PRIVATE assets only ever get one price row (stamped today by
                    // PrivateMarketDataProvider) — use it in preference to purchase
                    // cost for every other valuation date.
                    val latest = latestPriceIndex.getValue(key)
                    log.debug(
                        "No price for PRIVATE asset {} on {}, using latest known price {} from {}",
                        key,
                        dateStr,
                        latest.close,
                        latest.priceDate
                    )
                    latest.close
                } else {
                    // Fall back to average cost when no market price is available
                    val avgCost = pos.moneyValues[Position.In.TRADE]?.averageCost ?: BigDecimal.ZERO
                    if (avgCost.signum() == 0) {
                        log.warn("No price or cost basis for {} on {}, skipping", key, dateStr)
                        continue
                    }
                    log.debug("No market price for {} on {}, using cost price {}", key, dateStr, avgCost)
                    avgCost
                }

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
        cumulativeDividends: List<BigDecimal>,
        firstTradeDate: LocalDate? = null
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

        return PerformanceResponse(PerformanceData(portfolio.currency, dataPoints, firstTradeDate))
    }

    private fun emptyResponse(portfolio: Portfolio): PerformanceResponse =
        PerformanceResponse(PerformanceData(portfolio.currency, emptyList()))

    private fun tryLoadFromCache(portfolioId: String): List<CachedSnapshot>? {
        if (!cacheService.isAvailable()) return null
        return try {
            val cached = cacheService.findAllSnapshots(portfolioId)
            cached.ifEmpty { null }
        } catch (e: DataAccessException) {
            log.warn("Cache lookup failed, proceeding without cache: {}", e.message)
            null
        }
    }

    private fun tryCacheSnapshots(
        portfolioId: String,
        snapshots: List<ValuationSnapshot>,
        netContributions: List<BigDecimal>,
        cumulativeDividends: List<BigDecimal>
    ) {
        try {
            if (!cacheService.isAvailable()) return
            val toStore =
                snapshots.mapIndexed { index, snapshot ->
                    CachedSnapshot(
                        valuationDate = snapshot.date,
                        marketValue = snapshot.marketValue,
                        externalCashFlow = snapshot.externalCashFlow,
                        netContributions = netContributions.getOrElse(index) { BigDecimal.ZERO },
                        cumulativeDividends = cumulativeDividends.getOrElse(index) { BigDecimal.ZERO }
                    )
                }
            cacheService.storeSnapshots(portfolioId, toStore)
        } catch (e: DataAccessException) {
            log.warn("Cache store failed, computation unaffected: {}", e.message)
        }
    }

    /**
     * Returns cached snapshots restricted to the requested window with a
     * baseline anchor at exactly `startDate`. When the cache lacks a snapshot
     * on that date, synthesize one by carrying forward the latest cached
     * snapshot strictly before `startDate`.
     *
     * `netContributions` and `cumulativeDividends` carry exactly because every
     * cash flow date is itself a valuation date (see `determineValuationDates`),
     * so nothing flows between two cached snapshots. `marketValue` is an
     * approximation — at most ~1 month stale, bounded by the monthly cadence
     * of valuation dates between cash-flow events.
     *
     * Without this anchor, multi-portfolio frontends that union per-portfolio
     * dates see one portfolio's series start mid-window, leaking that
     * portfolio's lifetime gain into period-relative metrics.
     */
    internal fun anchorToStartDate(
        cached: List<CachedSnapshot>,
        startDate: LocalDate
    ): List<CachedSnapshot> {
        val onOrAfter = cached.filter { !it.valuationDate.isBefore(startDate) }
        if (onOrAfter.firstOrNull()?.valuationDate == startDate) return onOrAfter
        val prior = cached.lastOrNull { it.valuationDate.isBefore(startDate) } ?: return onOrAfter
        val anchor =
            CachedSnapshot(
                valuationDate = startDate,
                marketValue = prior.marketValue,
                externalCashFlow = BigDecimal.ZERO,
                netContributions = prior.netContributions,
                cumulativeDividends = prior.cumulativeDividends
            )
        return listOf(anchor) + onOrAfter
    }

    private fun buildResponseFromCache(
        portfolio: Portfolio,
        cached: List<CachedSnapshot>
    ): PerformanceResponse {
        val snapshots =
            cached.map {
                ValuationSnapshot(
                    date = it.valuationDate,
                    marketValue = it.marketValue,
                    externalCashFlow = it.externalCashFlow
                )
            }
        val netContributions = cached.map { it.netContributions }
        val cumulativeDividends = cached.map { it.cumulativeDividends }
        return buildResponse(portfolio, snapshots, netContributions, cumulativeDividends)
    }

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

    /**
     * Aggregate per-portfolio TWR series into a single composite series in
     * `displayCurrency`. Uses each portfolio's existing cached / computed TWR
     * (`calculate()`) and combines with chained sub-period AUM-weighted
     * composite per GIPS.
     */
    fun aggregate(
        portfolios: List<Portfolio>,
        months: Int,
        displayCurrency: Currency
    ): AggregatedPerformanceResponse {
        if (portfolios.isEmpty()) {
            return AggregatedPerformanceResponse(AggregatedPerformanceData(displayCurrency))
        }
        val perPortfolio = portfolios.map { p -> p to calculate(p, months).data }
        val fxRates = fetchDisplayCurrencyRates(perPortfolio, displayCurrency)
        return composeAggregate(perPortfolio, displayCurrency, fxRates)
    }

    private fun fetchDisplayCurrencyRates(
        perPortfolio: List<Pair<Portfolio, PerformanceData>>,
        displayCurrency: Currency
    ): Map<String, BigDecimal> {
        val pairs =
            perPortfolio
                .mapNotNull { (portfolio, _) ->
                    if (portfolio.currency.code == displayCurrency.code) {
                        null
                    } else {
                        IsoCurrencyPair(portfolio.currency.code, displayCurrency.code)
                    }
                }.toSet()

        val rates = mutableMapOf(displayCurrency.code to BigDecimal.ONE)
        if (pairs.isEmpty()) return rates

        val today = dateUtils.date.toString()
        val token = tokenService.bearerToken
        val response: BulkFxResponse =
            fxRateService.getBulkRates(
                BulkFxRequest(
                    startDate = today,
                    endDate = today,
                    pairs = pairs,
                    dates = listOf(today)
                ),
                token
            )

        // Single-rate-per-currency lookup: latest date in the response keyed by from-currency.
        val sortedDates = response.data.keys.sorted()
        val latest = sortedDates.lastOrNull()?.let { response.data[it] }
        if (latest != null) {
            for (pair in pairs) {
                latest.rates[pair]?.rate?.let { rates[pair.from] = it }
            }
        }
        return rates
    }

    /**
     * Pure composition step. Combines per-portfolio TWR series into a single
     * composite series in `displayCurrency`. No IO. Exposed `internal` for
     * direct unit testing of the weighting math.
     *
     * Algorithm (chained sub-period composite, GIPS):
     *   1. Build the union of valuation dates across portfolios.
     *   2. Forward-fill each portfolio's snapshots onto union dates.
     *   3. Convert market value / contributions / dividends to display ccy.
     *   4. For each sub-period [t_{i-1}, t_i]: sub-return = Σ w_p * r_p,
     *      where w_p = mv_p[i-1] / Σ mv[i-1] (display-ccy AUM) and
     *      r_p = growthFactor_p[i] / growthFactor_p[i-1] (TWR factor ratio).
     *      Portfolios with mv_p[i-1] == 0 are excluded from this sub-period.
     *   5. cumulative_factor[i] = cumulative_factor[i-1] * sub-return.
     *
     * Period-relative metrics (`netContributions`, `cumulativeDividends`,
     * `investmentGain`) are baselined against the first union point, so
     * `series[0]` reports zero for them.
     */
    internal fun composeAggregate(
        perPortfolio: List<Pair<Portfolio, PerformanceData>>,
        displayCurrency: Currency,
        fxRates: Map<String, BigDecimal>
    ): AggregatedPerformanceResponse {
        // Discard empty per-portfolio series. Sort each surviving series by date
        // and convert to display currency once.
        val perPortfolioFx: List<List<DisplaySnapshot>> =
            perPortfolio.mapNotNull { (portfolio, data) ->
                val series = data.series
                if (series.isEmpty()) return@mapNotNull null
                val rate = fxRates[portfolio.currency.code] ?: BigDecimal.ONE
                series
                    .sortedBy { it.date }
                    .map { p ->
                        DisplaySnapshot(
                            date = p.date,
                            growthFactor =
                                p.growthOf1000
                                    .divide(BigDecimal("1000"), 8, RoundingMode.HALF_UP),
                            mv = p.marketValue.multiply(rate),
                            contrib = p.netContributions.multiply(rate),
                            divs = p.cumulativeDividends.multiply(rate)
                        )
                    }
            }

        if (perPortfolioFx.isEmpty()) {
            return AggregatedPerformanceResponse(AggregatedPerformanceData(displayCurrency))
        }

        val sortedDates =
            perPortfolioFx
                .flatMap { snaps -> snaps.map { it.date } }
                .toSortedSet()
                .toList()

        // For each portfolio, forward-fill onto union dates. A portfolio with no
        // snapshot on/before a union date is "inactive" there (mv contribution 0,
        // excluded from weighting).
        val perPortfolioAligned: List<List<DisplaySnapshot?>> =
            perPortfolioFx.map { snaps ->
                var cursor = -1
                sortedDates.map { date ->
                    while (cursor + 1 < snaps.size && !snaps[cursor + 1].date.isAfter(date)) {
                        cursor++
                    }
                    if (cursor < 0) null else snaps[cursor]
                }
            }

        // Aggregate display-ccy totals per union date.
        val totals: List<AggregateRow> =
            sortedDates.indices.map { i ->
                var mv = BigDecimal.ZERO
                var contrib = BigDecimal.ZERO
                var divs = BigDecimal.ZERO
                for (p in perPortfolioAligned) {
                    val s = p[i] ?: continue
                    mv = mv.add(s.mv)
                    contrib = contrib.add(s.contrib)
                    divs = divs.add(s.divs)
                }
                AggregateRow(mv = mv, contrib = contrib, divs = divs)
            }

        // Chained sub-period composite TWR.
        val cumFactor = MutableList(sortedDates.size) { BigDecimal.ONE }
        for (i in 1 until sortedDates.size) {
            var numerator = BigDecimal.ZERO
            var weightSum = BigDecimal.ZERO
            for (p in perPortfolioAligned) {
                val prev = p[i - 1] ?: continue
                val curr = p[i] ?: continue
                if (prev.mv.signum() <= 0) continue
                if (prev.growthFactor.signum() <= 0) continue
                val r = curr.growthFactor.divide(prev.growthFactor, 10, RoundingMode.HALF_UP)
                val w = prev.mv
                numerator = numerator.add(w.multiply(r))
                weightSum = weightSum.add(w)
            }
            val subReturn =
                if (weightSum.signum() > 0) {
                    numerator.divide(weightSum, 10, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ONE
                }
            cumFactor[i] = cumFactor[i - 1].multiply(subReturn)
        }

        val baselineMv = totals[0].mv
        val baselineContrib = totals[0].contrib
        val baselineDivs = totals[0].divs

        val points =
            sortedDates.indices.map { i ->
                val row = totals[i]
                val periodContrib = row.contrib.subtract(baselineContrib)
                val periodDivs = row.divs.subtract(baselineDivs)
                val investmentGain =
                    row.mv
                        .subtract(baselineMv)
                        .subtract(periodContrib)
                val growthOf1000 =
                    cumFactor[i]
                        .multiply(BigDecimal("1000"))
                        .setScale(4, RoundingMode.HALF_UP)
                val cumulativeReturn =
                    cumFactor[i]
                        .subtract(BigDecimal.ONE)
                        .setScale(8, RoundingMode.HALF_UP)
                AggregatedPerformanceDataPoint(
                    date = sortedDates[i],
                    growthOf1000 = growthOf1000,
                    cumulativeReturn = cumulativeReturn,
                    marketValue = row.mv.setScale(2, RoundingMode.HALF_UP),
                    netContributions = periodContrib.setScale(2, RoundingMode.HALF_UP),
                    lifetimeContributions = row.contrib.setScale(2, RoundingMode.HALF_UP),
                    cumulativeDividends = periodDivs.setScale(2, RoundingMode.HALF_UP),
                    investmentGain = investmentGain.setScale(2, RoundingMode.HALF_UP)
                )
            }

        val xirr = computeAggregateXirr(perPortfolioFx)

        return AggregatedPerformanceResponse(
            AggregatedPerformanceData(currency = displayCurrency, series = points, xirr = xirr)
        )
    }

    /**
     * Pool per-portfolio investor cash flows over the window into a single
     * `PeriodicCashFlows` and solve for XIRR via the shared `IrrCalculator`.
     *
     * Sign convention is investor-POV:
     *   - At series[0] (window start): −marketValue (capital invested).
     *   - Between snapshots: −Δ netContributions (deposit = money out of wallet).
     *   - At series[last] (window end): +marketValue (capital realised).
     *
     * Inputs are already FX-converted to the display currency, so all flows
     * land in a single denomination. `IrrCalculator` itself handles short-
     * window fallback to simple ROI and bails to 0.0 when the solver fails.
     */
    private fun computeAggregateXirr(perPortfolioFx: List<List<DisplaySnapshot>>): BigDecimal? {
        if (perPortfolioFx.isEmpty()) return null
        val flows = mutableListOf<com.beancounter.common.model.CashFlow>()
        for (snaps in perPortfolioFx) {
            if (snaps.isEmpty()) continue
            val first = snaps.first()
            if (first.mv.signum() > 0) {
                flows.add(
                    com.beancounter.common.model.CashFlow(
                        amount = first.mv.negate().toDouble(),
                        date = first.date
                    )
                )
            }
            for (i in 1 until snaps.size) {
                val delta = snaps[i].contrib.subtract(snaps[i - 1].contrib)
                if (delta.signum() != 0) {
                    flows.add(
                        com.beancounter.common.model.CashFlow(
                            amount = delta.negate().toDouble(),
                            date = snaps[i].date
                        )
                    )
                }
            }
            val last = snaps.last()
            if (last.mv.signum() > 0) {
                flows.add(
                    com.beancounter.common.model.CashFlow(
                        amount = last.mv.toDouble(),
                        date = last.date
                    )
                )
            }
        }
        if (flows.size < 2) return null
        val periodic =
            com.beancounter.common.model
                .PeriodicCashFlows()
        periodic.addAll(flows)
        val xirr = irrCalculator.calculate(periodic)
        return BigDecimal(xirr).setScale(6, RoundingMode.HALF_UP)
    }

    private data class DisplaySnapshot(
        val date: LocalDate,
        val growthFactor: BigDecimal,
        val mv: BigDecimal,
        val contrib: BigDecimal,
        val divs: BigDecimal
    )

    private data class AggregateRow(
        val mv: BigDecimal,
        val contrib: BigDecimal,
        val divs: BigDecimal
    )

    companion object {
        private val log = LoggerFactory.getLogger(PerformanceService::class.java)

        // Matches PrivateMarketDataProvider.ID in svc-data: single latest-price
        // row, no daily history.
        private const val PRIVATE_MARKET = "PRIVATE"

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