package com.beancounter.position.irr

import com.beancounter.common.model.PeriodicCashFlows
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.analysis.solvers.BrentSolver
import org.apache.commons.math3.analysis.solvers.UnivariateSolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.pow

/**
 * Service class for calculating the Internal Rate of Return (IRR).
 */
@Service
class IrrCalculator(
    @Value("\${beancounter.irr:225}") private val minHoldingDays: Int = 225
) {
    private val solver: UnivariateSolver = BrentSolver()
    private val log = LoggerFactory.getLogger(IrrCalculator::class.java)

    /**
     * Calculates the IRR for a set of periodic cash flows.
     *
     * @param periodicCashFlows the periodic cash flows.
     * @param guess the initial guess for the IRR.
     * @return the calculated IRR or simpleRoi if the holding period is less than minHoldingDays.
     */
    fun calculate(
        periodicCashFlows: PeriodicCashFlows,
        roi: BigDecimal = BigDecimal.ZERO, // Default to ROI if not enough time to compute IRR
        guess: Double = 0.1
    ): Double {
        if (periodicCashFlows.cashFlows.isEmpty()) {
            log.warn("No cash flows to calculate IRR")
            return 0.0
        }

        val firstCashFlow = periodicCashFlows.cashFlows.minByOrNull { it.date }?.amount ?: BigDecimal.ZERO
        val lastCashFlow = periodicCashFlows.cashFlows.maxByOrNull { it.date }?.amount ?: BigDecimal.ZERO

        if (firstCashFlow == 0.0 || (lastCashFlow == 0.0 && firstCashFlow == 0.0)) {
            log.warn("Unable to calculate XIRR. First cash flow: $firstCashFlow, last cash flow: $lastCashFlow")
            return 0.0
        }
        log.trace("First cash flow: $firstCashFlow, last cash flow: $lastCashFlow")

        val firstDate = periodicCashFlows.cashFlows.minByOrNull { it.date }?.date ?: LocalDate.now()
        val lastDate = periodicCashFlows.cashFlows.maxByOrNull { it.date }?.date ?: LocalDate.now()

        return if (ChronoUnit.DAYS.between(
                firstDate,
                lastDate
            ) < minHoldingDays
        ) {
            calculateSimpleRoi(periodicCashFlows)
        } else {
            calculateXIRR(
                periodicCashFlows,
                guess,
                firstDate
            )
        }
    }

    fun calculateSimpleRoi(periodicCashFlows: PeriodicCashFlows): Double {
        val initialInvestment = periodicCashFlows.cashFlows.first().amount
        val finalValue = periodicCashFlows.cashFlows.sumOf { it.amount }
        return finalValue / initialInvestment.absoluteValue
    }

    private fun calculateXIRR(
        periodicCashFlows: PeriodicCashFlows,
        guess: Double,
        firstDate: LocalDate
    ): Double {
        val npvFunction =
            NPVFunction(
                periodicCashFlows,
                firstDate
            )

        val result =
            solver.solve(
                1000,
                npvFunction,
                -0.999999,
                1000000.0,
                guess
            )
        return String
            .format(
                "%.8f",
                result
            ).toDouble()
    }

    private class NPVFunction(
        private val periodicCashFlows: PeriodicCashFlows,
        private val startDate: LocalDate
    ) : UnivariateFunction {
        override fun value(rate: Double): Double =
            periodicCashFlows.cashFlows.sumOf {
                it.amount /
                    (1 + rate).pow(
                        ChronoUnit.DAYS.between(
                            startDate,
                            it.date
                        ) / 365.0
                    )
            }
    }
}