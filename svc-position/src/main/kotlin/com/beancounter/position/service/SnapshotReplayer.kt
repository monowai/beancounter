package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.cache.CachedSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Replays a portfolio's trns in date order and values it on each valuation date,
 * recording the flows that cross the [TwrBoundary] on the way.
 */
@Component
class SnapshotReplayer(
    private val accumulator: Accumulator,
    private val boundary: TwrBoundary
) {
    /**
     * When to value the portfolio: at each flow event plus the first of each month,
     * and always [startDate] and [endDate], all within the range.
     */
    fun valuationDates(
        transactions: List<Trn>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        val cashFlowDates =
            transactions
                .filter { boundary.isFlowEvent(it) }
                .filter { !it.tradeDate.isBefore(startDate) && !it.tradeDate.isAfter(endDate) }
                .map { it.tradeDate }

        return (cashFlowDates + monthlyDates(startDate, endDate) + listOf(startDate, endDate))
            .filter { !it.isBefore(startDate) && !it.isAfter(endDate) }
            .distinct()
            .sorted()
    }

    /** One snapshot per date in [valuationDates], each in [portfolio]'s currency. */
    fun replay(
        portfolio: Portfolio,
        transactions: List<Trn>,
        valuationDates: List<LocalDate>,
        prices: HistoricalPrices
    ): List<CachedSnapshot> {
        val replay = SnapshotReplay(portfolio, transactions, prices, accumulator, boundary)
        return valuationDates.map { replay.snapshotOn(it) }
    }

    /** The first of each month after [startDate], up to [endDate]. */
    private fun monthlyDates(
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
}

/**
 * One pass over a portfolio's trns. Positions and running totals advance with each
 * [snapshotOn] call, so dates must be asked for in order.
 */
private class SnapshotReplay(
    private val portfolio: Portfolio,
    private val transactions: List<Trn>,
    private val prices: HistoricalPrices,
    private val accumulator: Accumulator,
    private val boundary: TwrBoundary
) {
    private val positions = Positions(portfolio)
    private var trnIndex = 0
    private var netContributions = BigDecimal.ZERO
    private var cumulativeDividends = BigDecimal.ZERO

    fun snapshotOn(valDate: LocalDate): CachedSnapshot {
        val cashFlowOnDate = advanceTo(valDate)
        return CachedSnapshot(
            valuationDate = valDate,
            marketValue = prices.on(valDate).marketValue(positions, portfolio.currency),
            externalCashFlow = cashFlowOnDate,
            netContributions = netContributions,
            cumulativeDividends = cumulativeDividends
        )
    }

    /**
     * Accumulates trns up to and including [valDate], folding contributions and
     * dividends onto the running totals. Returns the flow dated [valDate].
     */
    private fun advanceTo(valDate: LocalDate): BigDecimal {
        var cashFlowOnDate = BigDecimal.ZERO
        while (trnIndex < transactions.size && !transactions[trnIndex].tradeDate.isAfter(valDate)) {
            val trn = transactions[trnIndex]
            val amount = accumulateFlow(trn)
            if (amount != null) {
                netContributions = netContributions.add(amount)
                if (trn.tradeDate == valDate) {
                    cashFlowOnDate = cashFlowOnDate.add(amount)
                }
            }
            // An excluded asset's dividend is money in from outside TWR (a flow above),
            // not income earned by the assets TWR measures.
            if (trn.trnType == TrnType.DIVI && !boundary.isExcluded(trn.asset)) {
                cumulativeDividends = cumulativeDividends.add(trn.tradeAmountIn(portfolio))
            }
            trnIndex++
        }
        return cashFlowOnDate
    }

    /**
     * Accumulates [trn] and returns the external flow it carries across the TWR
     * boundary, in portfolio currency, or null when it carries none.
     *
     * - DEPOSIT, WITHDRAWAL and the other [TwrBoundary.isExternalCashFlow] types are flows.
     * - A BALANCE on an included asset restates the balance. The rise in its basis is
     *   fresh principal, so it is a flow. For a non-cash asset that basis is the cost
     *   [com.beancounter.position.accumulation.BalanceBehaviour] keeps, which grows only
     *   by the contribution. What is left over is return.
     * - The cash leg of a trade in an excluded asset moves money between cash
     *   inside TWR and an asset outside it, so it is a flow too.
     * - ADD and REDUCE move units in or out without cash (an in-specie transfer). The
     *   flow is the units' market value on the trade date, priced as the snapshot
     *   prices them, so the transfer itself is neither return nor loss. The trn's own
     *   price is often the original cost carried over and is not used.
     * - A trade or dividend with no cash asset settles outside the portfolio: a buy is
     *   funded from outside and sale proceeds or a dividend leave at once. The trade
     *   amount is the flow, so a realised gain stays in the return when its proceeds go.
     */
    private fun accumulateFlow(trn: Trn): BigDecimal? {
        val basisBefore = boundary.balanceBasis(trn, positions, portfolio)
        val inKindBefore = inKindState(trn)
        accumulator.accumulate(trn, positions)
        return when {
            TwrBoundary.isExternalCashFlow(trn.trnType) -> trn.externalFlowIn(portfolio)
            basisBefore != null -> boundary.balanceBasis(trn, positions, portfolio)?.subtract(basisBefore)
            boundary.isExcludedCashLeg(trn) -> trn.cashLegIn(portfolio)
            boundary.isSettledOutside(trn) -> boundary.settledOutsideFlow(trn, portfolio)
            inKindBefore != null -> inKindValue(trn, inKindBefore)
            else -> null
        }
    }

    /** The position as an in-specie ADD or REDUCE of an included asset finds it; null for any other trn. */
    private fun inKindState(trn: Trn): InKindState? {
        if (!boundary.isInKindTransfer(trn)) return null
        val position = positions.getOrCreate(trn)
        return InKindState(
            position.quantityValues.getTotal(),
            prices.on(trn.tradeDate).unitValue(position, portfolio.currency)
        )
    }

    /**
     * Market value, in portfolio currency, of the units an in-specie trn moved. An asset
     * with no market price is carried at average cost, which a first ADD only sets and
     * a REDUCE to zero clears, so the value is read after the trn and, failing that,
     * [before] it.
     */
    private fun inKindValue(
        trn: Trn,
        before: InKindState
    ): BigDecimal? {
        val after = inKindState(trn) ?: return null
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

    /** A position's units and per-unit value on a trn's trade date, in portfolio currency. */
    private class InKindState(
        val units: BigDecimal,
        val unitValue: BigDecimal?
    )

    companion object {
        private val log = LoggerFactory.getLogger(SnapshotReplayer::class.java)
    }
}