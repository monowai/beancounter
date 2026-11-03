package com.beancounter.position.performance

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.EnsureHistoryRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * What a performance pass reads from bc-data: a portfolio's trns, then every price
 * and FX rate its valuation dates need in two bulk calls, so no date costs a
 * round-trip of its own.
 */
@Component
class PerformanceInputs(
    private val trnService: TrnService,
    private val priceService: PriceService,
    private val fxRateService: FxService,
    private val tokenService: TokenService,
    private val boundary: TwrBoundary,
    @Qualifier("performanceNudgeExecutor")
    private val nudgeExecutor: Executor = Executor(Runnable::run)
) {
    private val cashUtils = CashUtils()

    /** [portfolio]'s trns to date, oldest first. */
    fun transactions(portfolio: Portfolio): List<Trn> =
        trnService
            .query(portfolio, DateUtils.TODAY)
            .data
            .toTrns()
            .sortedBy { it.tradeDate }

    /**
     * Prices and FX rates for every date in [valuationDates], which always open and
     * close the requested window. The assets valued are
     * nudged for deep price history, or collected into [historyAssetIds] when the
     * caller sends one nudge for several portfolios.
     */
    fun prices(
        portfolio: Portfolio,
        transactions: List<Trn>,
        valuationDates: List<LocalDate>,
        historyAssetIds: MutableSet<String>?
    ): HistoricalPrices {
        val token = tokenService.bearerToken
        val startDate = valuationDates.first()
        val assets = collectAssets(transactions)

        // Fire-and-forget — nudge svc-data to backfill deep history for the
        // assets we're about to value. This lets long-window growth / wealth
        // charts converge to a complete series across requests without
        // blocking the current calculation on a multi-year provider call.
        val assetIds = assets.mapNotNull { it.resolvedAsset?.id ?: it.assetId.takeIf { id -> id.isNotEmpty() } }
        when (historyAssetIds) {
            null -> ensureAssetHistory(assetIds, startDate, token)
            else -> historyAssetIds.addAll(assetIds)
        }
        return HistoricalPrices(
            prefetchPrices(assets, valuationDates, token),
            prefetchFxRates(collectFxPairs(transactions, portfolio), valuationDates, token),
            boundary
        )
    }

    /** Asks svc-data to backfill price history for [assetIds] from [startDate], without waiting. */
    fun ensureAssetHistory(
        assetIds: Collection<String>,
        startDate: LocalDate
    ) {
        if (assetIds.isEmpty()) return
        ensureAssetHistory(assetIds, startDate, tokenService.bearerToken)
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

    /**
     * One rate per portfolio currency into [displayCurrency] on [today], keyed by the
     * source currency code; the display currency itself is 1.
     */
    fun displayCurrencyRates(
        perPortfolio: List<Pair<Portfolio, PerformanceData>>,
        displayCurrency: Currency,
        today: LocalDate
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

        val date = today.toString()
        val response =
            fxRateService.getBulkRates(
                BulkFxRequest(startDate = date, endDate = date, pairs = pairs, dates = listOf(date)),
                tokenService.bearerToken
            )

        // Single-rate-per-currency lookup: latest date in the response keyed by from-currency.
        val latest =
            response.data.keys
                .sorted()
                .lastOrNull()
                ?.let { response.data[it] }
        if (latest != null) {
            for (pair in pairs) {
                latest.rates[pair]?.rate?.let { rates[pair.from] = it }
            }
        }
        return rates
    }

    private fun collectAssets(transactions: List<Trn>): List<PriceAsset> =
        transactions
            .map { it.asset }
            .filter { !cashUtils.isCash(it) && !boundary.isExcluded(it) }
            .distinctBy { it.id }
            .map { PriceAsset(it) }

    private fun collectFxPairs(
        transactions: List<Trn>,
        portfolio: Portfolio
    ): Set<IsoCurrencyPair> = transactions.mapNotNull { trn -> toPair(trn.tradeCurrency, portfolio.currency) }.toSet()

    private fun prefetchPrices(
        assets: List<PriceAsset>,
        dates: List<LocalDate>,
        token: String
    ): Map<String, Collection<MarketData>> {
        if (assets.isEmpty()) return emptyMap()
        val request = BulkPriceRequest(dates = dates.map { it.toString() }, assets = assets)
        return priceService.getBulkPrices(request, token).data
    }

    private fun prefetchFxRates(
        pairs: Set<IsoCurrencyPair>,
        valuationDates: List<LocalDate>,
        token: String
    ): Map<String, FxPairResults> {
        if (pairs.isEmpty()) return emptyMap()
        val request =
            BulkFxRequest(
                startDate = valuationDates.first().toString(),
                endDate = valuationDates.last().toString(),
                pairs = pairs,
                // Send the exact valuation dates so svc-data narrows its DB load to
                // those + a small lookback. Sending only [startDate, endDate] would
                // eager-load every cached FxRate in range (10y for the "ALL" wealth
                // chart) and OOM bc-data — DATA-45, 2026-05-18.
                dates = valuationDates.map { it.toString() }
            )
        return fxRateService.getBulkRates(request, token).data
    }

    companion object {
        private val log = LoggerFactory.getLogger(PerformanceInputs::class.java)
    }
}