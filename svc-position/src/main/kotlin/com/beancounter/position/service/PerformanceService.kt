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
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.cache.CachedSnapshot
import com.beancounter.position.cache.PerformanceCacheService
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.irr.ValuationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
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
        val assetIds = allAssets.mapNotNull { it.resolvedAsset?.id ?: it.assetId.takeIf { id -> id.isNotEmpty() } }
        when (historyAssetIds) {
            null -> ensureAssetHistory(assetIds, startDate, token)
            else -> historyAssetIds.addAll(assetIds)
        }

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
            .filter { !cashUtils.isCash(it) && !isExcluded(it) }
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
        assetIds: Collection<String>,
        startDate: LocalDate,
        token: String
    ) {
        if (assetIds.isEmpty()) return
        // Truly fire-and-forget: dispatch the HTTP call onto a dedicated executor so
        // the request thread doesn't wait on the round-trip to svc-data. svc-data
        // returns immediately (it only schedules the backfill), but even the bare
        // RPC adds latency we don't want on every calculate().
        nudgeExecutor.execute {
            try {
                priceService.ensureHistory(
                    EnsureHistoryRequest(assetIds = assetIds.distinct(), fromDate = startDate),
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
                .filter { isFlowEvent(it) }
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
        var acc = DateAccumulation(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        // Computed once per pass (not per date) — PRIVATE-market assets only
        // ever get one price row (see valuePositionsFromCache), so every other
        // valuation date needs this fallback.
        val prices = PassPrices(priceCache, fxCache, buildLatestPriceIndex(priceCache))

        for (valDate in valuationDates) {
            // Accumulate transactions up to and including this date
            acc = accumulateUpTo(valDate, transactions, positions, portfolio, acc, prices)

            val marketValue = valuePositionsFromCache(positions, valDate, portfolio, prices)

            snapshots.add(
                ValuationSnapshot(
                    date = valDate,
                    marketValue = marketValue,
                    externalCashFlow = acc.cashFlowOnDate
                )
            )
            netContributionsList.add(acc.netContributions)
            cumulativeDividendsList.add(acc.cumulativeDividends)
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

    /** One pass's pre-fetched prices and FX rates, keyed by ISO date. */
    private data class PassPrices(
        val priceCache: Map<String, Collection<MarketData>>,
        val fxCache: Map<String, FxPairResults>,
        val latestPriceIndex: Map<String, MarketData>
    )

    /**
     * Accumulates transactions into [positions] up to and including [valDate],
     * advancing from the [prior] date's trn index and folding contributions/dividends
     * onto its running totals. Mutates [positions]; returns the updated scalar totals.
     */
    private fun accumulateUpTo(
        valDate: LocalDate,
        transactions: List<Trn>,
        positions: Positions,
        portfolio: Portfolio,
        prior: DateAccumulation,
        prices: PassPrices
    ): DateAccumulation {
        var trnIndex = prior.trnIndex
        var netContributions = prior.netContributions
        var cumulativeDividends = prior.cumulativeDividends
        var cashFlowOnDate = BigDecimal.ZERO

        while (trnIndex < transactions.size && !transactions[trnIndex].tradeDate.isAfter(valDate)) {
            val trn = transactions[trnIndex]
            val amount = accumulateFlow(trn, positions, portfolio, prices)

            if (amount != null) {
                netContributions = netContributions.add(amount)
                if (trn.tradeDate == valDate) {
                    cashFlowOnDate = cashFlowOnDate.add(amount)
                }
            }

            // An excluded asset's dividend is money in from outside TWR (a flow above),
            // not income earned by the assets TWR measures.
            if (trn.trnType == TrnType.DIVI && !isExcluded(trn.asset)) {
                cumulativeDividends =
                    cumulativeDividends.add(tradeAmountInPortfolioCurrency(trn, portfolio))
            }
            trnIndex++
        }

        return DateAccumulation(trnIndex, netContributions, cumulativeDividends, cashFlowOnDate)
    }

    /**
     * Accumulates [trn] into [positions] and returns the external flow it carries
     * across the TWR boundary, in portfolio currency, or null when it carries none.
     *
     * - DEPOSIT, WITHDRAWAL and the other [isExternalCashFlow] types are flows.
     * - A BALANCE on an included asset restates the balance. The rise in its basis is
     *   fresh principal, so it is a flow. For a non-cash asset that basis is the cost
     *   [com.beancounter.position.accumulation.BalanceBehaviour] keeps, which grows only
     *   by the contribution. What is left over is return.
     * - The cash leg of a trade in an [isExcluded] asset moves money between cash
     *   inside TWR and an asset outside it, so it is a flow too.
     * - ADD and REDUCE move units in or out without cash (an in-specie transfer). The
     *   flow is the units' market value on the trade date, priced as the snapshot
     *   prices them, so the transfer itself is neither return nor loss. The trn's own
     *   price is often the original cost carried over and is not used.
     * - A trade or dividend with no cash asset settles outside the portfolio: a buy is
     *   funded from outside and sale proceeds or a dividend leave at once. The trade
     *   amount is the flow, so a realised gain stays in the return when its proceeds go.
     */
    private fun accumulateFlow(
        trn: Trn,
        positions: Positions,
        portfolio: Portfolio,
        prices: PassPrices
    ): BigDecimal? {
        val basisBefore = balanceBasis(trn, positions, portfolio)
        val inKindBefore = inKindState(trn, positions, portfolio, prices)
        accumulator.accumulate(trn, positions)
        return when {
            isExternalCashFlow(trn.trnType) -> convertCashFlowToPortfolioCurrency(trn, portfolio)
            basisBefore != null -> balanceBasis(trn, positions, portfolio)?.subtract(basisBefore)
            isExcludedCashLeg(trn) -> cashLegInPortfolioCurrency(trn, portfolio)
            isSettledOutside(trn) -> settledOutsideFlow(trn, portfolio)
            inKindBefore != null -> inKindValue(trn, positions, inKindBefore, portfolio, prices)
            else -> null
        }
    }

    /** A position's units and per-unit value on a trn's trade date, in portfolio currency. */
    private data class InKindState(
        val units: BigDecimal,
        val unitValue: BigDecimal?
    )

    /** The position as an in-specie ADD or REDUCE of an included asset finds it; null for any other trn. */
    private fun inKindState(
        trn: Trn,
        positions: Positions,
        portfolio: Portfolio,
        prices: PassPrices
    ): InKindState? {
        if (!isInKindTransfer(trn)) return null
        val position = positions.getOrCreate(trn)
        val dateStr = trn.tradeDate.toString()
        return InKindState(
            position.quantityValues.getTotal(),
            unitValue(
                position,
                dateStr,
                portfolio,
                priceMapFor(dateStr, prices),
                findNearestFxRates(dateStr, prices.fxCache),
                prices
            )
        )
    }

    private fun isInKindTransfer(trn: Trn): Boolean =
        (trn.trnType == TrnType.ADD || trn.trnType == TrnType.REDUCE) && !isExcluded(trn.asset)

    /**
     * Market value, in portfolio currency, of the units an in-specie trn moved. An asset
     * with no market price is carried at average cost, which a first ADD only sets and
     * a REDUCE to zero clears, so the value is read after the trn and, failing that,
     * [before] it.
     */
    private fun inKindValue(
        trn: Trn,
        positions: Positions,
        before: InKindState,
        portfolio: Portfolio,
        prices: PassPrices
    ): BigDecimal? {
        val after = inKindState(trn, positions, portfolio, prices) ?: return null
        val unitValue = after.unitValue ?: before.unitValue
        if (unitValue == null) {
            log.warn(
                "No price or cost basis for in-specie {} of {} on {}; left out of TWR flows",
                trn.trnType,
                trn.asset.code,
                trn.tradeDate
            )
            return null
        }
        return after.units
            .subtract(before.units)
            .multiply(unitValue)
            .setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * The basis a BALANCE on an included asset restates, in portfolio currency: the
     * balance itself for cash, the cost basis otherwise. Null for any other trn.
     */
    private fun balanceBasis(
        trn: Trn,
        positions: Positions,
        portfolio: Portfolio
    ): BigDecimal? {
        if (trn.trnType != TrnType.BALANCE || isExcluded(trn.asset)) return null
        val position = positions.getOrCreate(trn)
        if (!cashUtils.isCash(trn.asset)) {
            return position.moneyValues[Position.In.PORTFOLIO]?.costBasis ?: BigDecimal.ZERO
        }
        val balance = position.quantityValues.getTotal()
        return if (trn.tradeCurrency.code == portfolio.currency.code) {
            balance
        } else {
            balance.multiply(trn.tradePortfolioRate).setScale(2, RoundingMode.HALF_UP)
        }
    }

    /**
     * A cash-impacting trade in an included asset that names no cash asset: the
     * accumulator moves no cash for it, so the money passed outside the portfolio.
     */
    private fun isSettledOutside(trn: Trn): Boolean =
        trn.cashAsset == null &&
            TrnType.isCashImpacted(trn.trnType) &&
            !isExternalCashFlow(trn.trnType) &&
            !cashUtils.isCash(trn.asset) &&
            !isExcluded(trn.asset)

    /** Money in for a buy, money out for sale proceeds or a dividend, in portfolio currency. */
    private fun settledOutsideFlow(
        trn: Trn,
        portfolio: Portfolio
    ): BigDecimal {
        val amount = tradeAmountInPortfolioCurrency(trn, portfolio)
        return if (TrnType.isCashCredited(trn.trnType)) amount.negate() else amount
    }

    private fun isExcludedCashLeg(trn: Trn): Boolean =
        isExcluded(trn.asset) && trn.cashAsset != null && TrnType.isCashImpacted(trn.trnType)

    /**
     * Converts a trade's signed cash leg (cash currency) to portfolio currency
     * through the trade's stored rates: cash -> trade via tradeCashRate, then
     * trade -> portfolio via tradePortfolioRate.
     */
    private fun cashLegInPortfolioCurrency(
        trn: Trn,
        portfolio: Portfolio
    ): BigDecimal {
        val cashCode = (trn.cashCurrency ?: trn.tradeCurrency).code
        if (cashCode == portfolio.currency.code) return trn.cashAmount
        val inTrade =
            if (cashCode == trn.tradeCurrency.code || trn.tradeCashRate.signum() == 0) {
                trn.cashAmount
            } else {
                trn.cashAmount.divide(trn.tradeCashRate, CASH_LEG_SCALE, RoundingMode.HALF_UP)
            }
        val inPortfolio =
            if (trn.tradeCurrency.code == portfolio.currency.code) inTrade else inTrade.multiply(trn.tradePortfolioRate)
        return inPortfolio.setScale(2, RoundingMode.HALF_UP)
    }

    private fun isFlowEvent(trn: Trn): Boolean =
        isExternalCashFlow(trn.trnType) ||
            (trn.trnType == TrnType.BALANCE && !isExcluded(trn.asset)) ||
            isExcludedCashLeg(trn) ||
            isSettledOutside(trn) ||
            isInKindTransfer(trn)

    /**
     * PRIVATE-market assets stay out of TWR unless [Asset.includeInPerformance]
     * is set: their valuations move on appraisal or snapshot cycles, not market prices.
     * Cash-like PRIVATE assets (bank accounts) are cash and always stay in.
     */
    private fun isExcluded(asset: Asset): Boolean =
        asset.market.code == PRIVATE_MARKET && !cashUtils.isCash(asset) && !asset.includeInPerformance

    private fun tradeAmountInPortfolioCurrency(
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
     *   marketValue = quantity * [unitValue]
     *
     * Excluded PRIVATE assets are left out; see [isExcluded].
     */
    private fun valuePositionsFromCache(
        positions: Positions,
        date: LocalDate,
        portfolio: Portfolio,
        prices: PassPrices
    ): BigDecimal {
        val dateStr = date.toString()
        val fxRates = findNearestFxRates(dateStr, prices.fxCache)
        val priceMap = priceMapFor(dateStr, prices)

        var totalMv = BigDecimal.ZERO
        for (pos in positions.positions.values) {
            if (pos.quantityValues.getTotal().signum() == 0 || isExcluded(pos.asset)) continue
            val unitValue = unitValue(pos, dateStr, portfolio, priceMap, fxRates, prices) ?: continue
            val positionMv = pos.quantityValues.getTotal().multiply(unitValue)
            totalMv =
                totalMv.add(
                    if (cashUtils.isCash(pos.asset)) positionMv else positionMv.setScale(2, RoundingMode.HALF_UP)
                )
        }
        return totalMv
    }

    private fun priceMapFor(
        dateStr: String,
        prices: PassPrices
    ): Map<String, MarketData> =
        (prices.priceCache[dateStr] ?: emptyList()).associateBy { "${it.asset.market.code}:${it.asset.code}" }

    /**
     * Value of one unit of [pos] on [dateStr] in portfolio currency: price * fx(trade -> portfolio).
     * A cash unit is worth one of its currency, so needs no price. Null when a non-cash asset
     * has neither a price nor a cost basis to fall back on.
     */
    private fun unitValue(
        pos: Position,
        dateStr: String,
        portfolio: Portfolio,
        priceMap: Map<String, MarketData>,
        fxRates: FxPairResults?,
        prices: PassPrices
    ): BigDecimal? {
        val isCash = cashUtils.isCash(pos.asset)
        val price = if (isCash) BigDecimal.ONE else unitPrice(pos, dateStr, priceMap, prices) ?: return null
        val tradeCcy = pos.moneyValues[Position.In.TRADE]?.currency ?: pos.asset.market.currency
        if (tradeCcy.code == portfolio.currency.code) return price
        val fxPair = IsoCurrencyPair(tradeCcy.code, portfolio.currency.code)
        val rate = fxRates?.rates?.get(fxPair)?.rate
        if (rate == null) {
            log.warn(
                "Missing FX rate for {} {} on {}, defaulting to 1.0",
                if (isCash) "cash position" else "position",
                fxPair,
                dateStr
            )
        }
        return price.multiply(rate ?: BigDecimal.ONE)
    }

    /** Close on [dateStr], else a PRIVATE asset's latest known price, else average cost. */
    private fun unitPrice(
        pos: Position,
        dateStr: String,
        priceMap: Map<String, MarketData>,
        prices: PassPrices
    ): BigDecimal? {
        val key = "${pos.asset.market.code}:${pos.asset.code}"
        priceMap[key]?.let { return it.close }
        val latest = prices.latestPriceIndex[key]
        if (pos.asset.market.code == PRIVATE_MARKET && latest != null) {
            // PRIVATE assets only ever get one price row (stamped today by
            // PrivateMarketDataProvider) — use it in preference to purchase
            // cost for every other valuation date.
            log.debug(
                "No price for PRIVATE asset {} on {}, using latest known price {} from {}",
                key,
                dateStr,
                latest.close,
                latest.priceDate
            )
            return latest.close
        }
        // Fall back to average cost when no market price is available
        val avgCost = pos.moneyValues[Position.In.TRADE]?.averageCost ?: BigDecimal.ZERO
        if (avgCost.signum() == 0) {
            log.warn("No price or cost basis for {} on {}, skipping", key, dateStr)
            return null
        }
        log.debug("No market price for {} on {}, using cost price {}", key, dateStr, avgCost)
        return avgCost
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
        val perPortfolio = calculateAll(portfolios, months)
        val fxRates = fetchDisplayCurrencyRates(perPortfolio, displayCurrency)
        return composeAggregate(perPortfolio, displayCurrency, fxRates)
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
        ensureAssetHistory(historyAssetIds, dateUtils.date.minusMonths(months.toLong()), tokenService.bearerToken)
        return perPortfolio
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

        private const val CASH_LEG_SCALE = 10

        // Portfolios calculated at once by [aggregate]. Each cold calculation makes bulk
        // price and FX calls to bc-data, whose 512m heap is the constraint.
        private const val AGGREGATE_CONCURRENCY = 4

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