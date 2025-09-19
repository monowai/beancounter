package com.beancounter.position.irr

import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.utils.DateUtils
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.analysis.solvers.BrentSolver
import org.apache.commons.math3.analysis.solvers.UnivariateSolver
import org.apache.commons.math3.exception.NoBracketingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.pow

/**
 * Service class for calculating the Internal Rate of Return (IRR).
 */
@Service
class IrrCalculator(
    @param:Value($$"${beancounter.irr:225}") private val minHoldingDays: Int = 225,
    private val dateUtils: DateUtils
) {
    private val solver: UnivariateSolver = BrentSolver()
    private val log = LoggerFactory.getLogger(IrrCalculator::class.java)

    companion object {
        // IRR solver configuration constants
        private const val MIN_RATE_BOUND = -0.8 // -80% annual loss (conservative lower bound)
        private const val MAX_RATE_BOUND = 5.0 // 500% annual gain (aggressive upper bound)
        private const val CONSERVATIVE_LOWER_BOUND = -0.95 // -95% for conservative scenarios
        private const val EXTREME_LOWER_BOUND = -0.999999 // Near-complete loss scenarios
        private const val EXTREME_UPPER_BOUND = 10.0 // 1000% annual return upper bound
        private const val RATE_ADJUSTMENT_FACTOR = 1.0 // For bound adjustments around initial guess
        private const val WIDE_BOUND_ADJUSTMENT = 2.0 // Wider adjustment for better initial guesses

        // Solver iteration limits based on complexity
        private const val SIMPLE_MAX_ITERATIONS = 100 // For <= 5 cash flows
        private const val MEDIUM_MAX_ITERATIONS = 500 // For 6-20 cash flows
        private const val COMPLEX_MAX_ITERATIONS = 1000 // For 20+ cash flows

        // Cash flow complexity thresholds
        private const val SIMPLE_CASH_FLOW_THRESHOLD = 5
        private const val MEDIUM_CASH_FLOW_THRESHOLD = 20

        // NPV calculation constants
        private const val UNITY_BASE = 1.0 // Base value for (1 + rate) calculations
    }

    /**
     * Calculates the IRR for a set of periodic cash flows.
     *
     * @param periodicCashFlows the periodic cash flows.
     * @return the calculated IRR or simpleRoi if the holding period is less than minHoldingDays.
     */
    fun calculate(periodicCashFlows: PeriodicCashFlows): Double {
        // Early returns for edge cases
        if (periodicCashFlows.cashFlows.isEmpty()) {
            log.warn("No cash flows to calculate IRR")
            return 0.0
        }

        if (periodicCashFlows.cashFlows.size == 1) {
            // Single cash flow cannot have an IRR
            return 0.0
        }

        // Quick check for clearly invalid cash flow patterns (only catching extreme cases)
        val allSameSign =
            periodicCashFlows.cashFlows.all { it.amount >= 0.0 } ||
                periodicCashFlows.cashFlows.all { it.amount <= 0.0 }
        val allZero = periodicCashFlows.cashFlows.all { it.amount == 0.0 }

        if (allZero) {
            log.warn("IRR calculation requires non-zero cash flows")
            return 0.0
        }

        // For strictly all positive or all negative (no zeros), early return
        if (allSameSign && !periodicCashFlows.cashFlows.any { it.amount == 0.0 }) {
            log.warn("IRR calculation requires both positive and negative cash flows")
            return 0.0
        }

        // Single pass to find min/max dates and corresponding amounts
        val sortedCashFlows = periodicCashFlows.cashFlows.sortedBy { it.date }
        val firstCashFlow = sortedCashFlows.first()
        val lastCashFlow = sortedCashFlows.last()

        if (firstCashFlow.amount == 0.0) {
            log.warn(
                "Unable to calculate XIRR. First cash flow: ${firstCashFlow.amount}, last cash flow: ${lastCashFlow.amount}"
            )
            return 0.0
        }
        log.trace("First cash flow: ${firstCashFlow.amount}, last cash flow: ${lastCashFlow.amount}")

        val firstDate = firstCashFlow.date
        val lastDate = lastCashFlow.date

        return if (ChronoUnit.DAYS.between(
                firstDate,
                lastDate
            ) < minHoldingDays
        ) {
            calculateSimpleRoi(periodicCashFlows)
        } else {
            calculateXIRR(
                periodicCashFlows,
                firstDate,
                sortedCashFlows
            )
        }
    }

    fun calculateSimpleRoi(periodicCashFlows: PeriodicCashFlows): Double {
        // Separate negative cash flows (investments/outflows) from positive (returns/inflows)
        val totalInvestment =
            periodicCashFlows.cashFlows
                .filter { it.amount < 0 }
                .sumOf { it.amount.absoluteValue }

        val totalReturns =
            periodicCashFlows.cashFlows
                .filter { it.amount > 0 }
                .sumOf { it.amount }

        if (totalInvestment == 0.0) {
            return 0.0
        }

        // ROI = (Returns - Investment) / Investment
        return (totalReturns - totalInvestment) / totalInvestment
    }

    private fun calculateXIRR(
        periodicCashFlows: PeriodicCashFlows,
        firstDate: LocalDate,
        sortedCashFlows: List<com.beancounter.common.model.CashFlow>? = null
    ): Double {
        val npvFunction =
            NPVFunction(
                periodicCashFlows,
                firstDate,
                dateUtils,
                sortedCashFlows
            )

        // Calculate precise holding period in years for annualizing the Simple ROI guess
        val lastDate =
            sortedCashFlows?.lastOrNull()?.date ?: periodicCashFlows.cashFlows.maxByOrNull { it.date }?.date
                ?: firstDate
        val holdingPeriodYears = dateUtils.calculateYearFraction(firstDate, lastDate)

        // Annualize the Simple ROI to get a better initial guess for the solver
        val simpleRoi = calculateSimpleRoi(periodicCashFlows)
        val initialGuess =
            if (holdingPeriodYears > 0.0) {
                // Convert total return to annualized rate: (1 + totalReturn)^(1/years) - 1
                (UNITY_BASE + simpleRoi).pow(UNITY_BASE / holdingPeriodYears) - UNITY_BASE
            } else {
                simpleRoi
            }.coerceIn(MIN_RATE_BOUND, MAX_RATE_BOUND)

        // Optimize max iterations based on cash flow complexity
        val maxIterations =
            when {
                periodicCashFlows.cashFlows.size <= SIMPLE_CASH_FLOW_THRESHOLD -> SIMPLE_MAX_ITERATIONS
                periodicCashFlows.cashFlows.size <= MEDIUM_CASH_FLOW_THRESHOLD -> MEDIUM_MAX_ITERATIONS
                else -> COMPLEX_MAX_ITERATIONS
            }

        // Use tighter bounds for better convergence when initial guess is reasonable
        val (lowerBound, upperBound) =
            if (initialGuess.isFinite() && initialGuess > MIN_RATE_BOUND && initialGuess < MAX_RATE_BOUND) {
                // Good initial guess - use tighter bounds for faster convergence
                Pair(
                    maxOf(CONSERVATIVE_LOWER_BOUND, initialGuess - RATE_ADJUSTMENT_FACTOR),
                    minOf(MAX_RATE_BOUND, initialGuess + WIDE_BOUND_ADJUSTMENT)
                )
            } else {
                // Poor initial guess - use wider bounds
                Pair(EXTREME_LOWER_BOUND, EXTREME_UPPER_BOUND)
            }

        try {
            val result = solver.solve(maxIterations, npvFunction, lowerBound, upperBound, initialGuess)
            return result
        } catch (e: NoBracketingException) {
            log.warn("XIRR solver failed with bounds [$lowerBound, $upperBound] and guess $initialGuess", e)
            // Fallback to simple ROI if solver fails
            return calculateSimpleRoi(periodicCashFlows)
        }
    }

    private class NPVFunction(
        private val periodicCashFlows: PeriodicCashFlows,
        private val startDate: LocalDate,
        private val dateUtils: DateUtils,
        sortedCashFlows: List<com.beancounter.common.model.CashFlow>? = null
    ) : UnivariateFunction {
        private val cashFlowsWithYearFractions: List<Pair<Double, Double>> =
            run {
                val flows = sortedCashFlows ?: periodicCashFlows.cashFlows.sortedBy { it.date }
                flows.map { cashFlow ->
                    val yearFraction = dateUtils.calculateYearFraction(startDate, cashFlow.date)
                    Pair(cashFlow.amount, yearFraction)
                }
            }

        override fun value(rate: Double): Double =
            cashFlowsWithYearFractions.sumOf { (amount, yearFraction) ->
                amount / (UNITY_BASE + rate).pow(yearFraction)
            }
    }
}