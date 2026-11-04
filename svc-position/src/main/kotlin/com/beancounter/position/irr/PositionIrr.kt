package com.beancounter.position.irr

import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Position
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The IRR a valuation reports, for one position or for a portfolio's combined cash flows.
 *
 * Simple ROI replaces XIRR for short periods, because XIRR annualises a short
 * holding into extreme figures. One threshold, [IrrCalculator.minHoldingDays]
 * (`beancounter.irr`), drives two checks that measure different spans:
 * - [ofPosition] measures the current holding, from the position's `opened` date to
 *   the valuation date. `opened` survives a sell-out and resets on re-entry, so a
 *   position re-bought last month reports ROI even when its first trade is years old.
 * - [IrrCalculator.calculate] measures the cash flows, first to last. It is the only
 *   check a portfolio's combined flows get, and it still applies to a position that
 *   passed the first.
 */
@Component
class PositionIrr(
    private val irrCalculator: IrrCalculator
) {
    /**
     * Anchor the IRR investment to the position's cost basis.
     *
     * A `BALANCE` snapshot (CPF, pensions) records its opening principal as a
     * cost-basis entry, NOT a cash transaction — so it is absent from the IRR
     * cash flows, while the terminal market value (added next) DOES include it.
     * That asymmetry makes the opening principal look like pure return (e.g. a
     * property-less CPF balance reporting a 3805% IRR). Cost basis is tracked
     * correctly (snapshot-aware) by BalanceBehaviour, so when the recorded
     * investment (sum of negative cash flows) falls short of it, prepend the
     * shortfall as an opening investment cash flow at the holding's open date.
     * For ordinary traded positions the cash flows already equal cost basis, so
     * the shortfall is ~0 and this is a no-op.
     */
    fun anchorOpeningCostBasis(
        position: Position,
        tradeMoneyValues: MoneyValues
    ) {
        val costBasis = tradeMoneyValues.costValue.toDouble()
        if (costBasis <= 0.0) return
        val recordedInvestment =
            position.periodicCashFlows.cashFlows
                .filter { it.amount < 0.0 }
                .sumOf { -it.amount }
        val shortfall = costBasis - recordedInvestment
        if (shortfall > COST_BASIS_SHORTFALL_TOLERANCE) {
            position.periodicCashFlows.cashFlows.add(
                0,
                CashFlow(
                    -shortfall,
                    position.dateValues.opened ?: position.dateValues.firstTransaction ?: return
                )
            )
        }
    }

    /**
     * IRR for [position] as at [asAtDate]: [roi] while the current holding is younger
     * than the threshold or has no cash flows, XIRR over its cash flows otherwise.
     */
    fun ofPosition(
        position: Position,
        asAtDate: LocalDate,
        roi: BigDecimal
    ): BigDecimal {
        val opened = position.dateValues.opened
        val holdingDays = if (opened == null) 0L else ChronoUnit.DAYS.between(opened, asAtDate)
        return if (holdingDays < irrCalculator.minHoldingDays || position.periodicCashFlows.cashFlows.isEmpty()) {
            roi
        } else {
            of(position.periodicCashFlows)
        }
    }

    /**
     * IRR of [periodicCashFlows]. Zero is a real answer: a 0% return, or a series with no
     * IRR (no sign change, no cost basis), which [IrrCalculator.calculate] logs itself.
     */
    fun of(periodicCashFlows: PeriodicCashFlows): BigDecimal =
        BigDecimal.valueOf(irrCalculator.calculate(periodicCashFlows))

    companion object {
        private const val COST_BASIS_SHORTFALL_TOLERANCE = 0.01
    }
}