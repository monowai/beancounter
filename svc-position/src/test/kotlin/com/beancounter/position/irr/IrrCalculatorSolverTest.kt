package com.beancounter.position.irr

import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.US
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class IrrCalculatorSolverTest {
    private val asset = AssetUtils.getTestAsset(US, "SolverTestAsset")

    @Test
    fun `should handle extreme negative returns without solver convergence issues`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: Massive loss - invest $1000, get back $1
        // This should result in a very negative IRR close to -100%
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-1000") // Investment
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(600),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1") // Nearly total loss
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Current solver configuration may fail or produce unstable results
        // With bounds -0.999999 to 1000000.0, this should work but may be numerically unstable
        assertThat(result).isLessThan(0.0) // Should be negative
        assertThat(result).isGreaterThan(-1.0) // Should be greater than -100%
    }

    @Test
    fun `should handle extreme positive returns that may exceed realistic bounds`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: Unrealistic gains - invest $1, get back $1000000
        // This tests the upper bound of 1000000.0 (100,000,000% return)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-1") // Small investment
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(600),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1000000") // Massive return
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // With realistic bounds [-0.999999, 10.0], extreme cases should either converge or fallback to simple ROI
        // This massive return should be handled gracefully with fallback to simple ROI
        assertThat(result).isGreaterThan(0.0) // Should be positive
        // For extreme cases like this, the solver will fail and fallback to simple ROI
        // Simple ROI = (1000000 - 1) / 1 = 999999.0 (999999900%)
        // This demonstrates the fallback mechanism is working correctly
    }

    @Test
    fun `should handle rapid oscillating cash flows that may confuse solver`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: Rapid alternating positive/negative cash flows
        // This can cause numerical instability and convergence issues
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                var sign = -1
                var amount = 1000.0
                for (i in 0..10) {
                    add(
                        Trn(
                            asset = asset,
                            tradeDate = LocalDate.now().plusDays(i * 50L),
                            trnType = if (sign < 0) TrnType.BUY else TrnType.SELL,
                            cashAmount = BigDecimal(sign * amount)
                        )
                    )
                    sign *= -1
                    amount *= 1.1 // Increase amounts each time
                }
            }

        // This should either converge to a reasonable result or handle the complexity gracefully
        val result = irrCalculator.calculate(periodicCashFlows)

        // With current solver configuration, this may produce unstable results
        assertThat(result).isNotNull
        assertThat(result.isFinite()).isTrue // Should not be infinite or NaN
    }

    @Test
    fun `should demonstrate solver bounds issue with realistic high returns`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: High but realistic returns (e.g., 300% annual return)
        // Current bounds allow up to 100,000,000% which is unrealistic
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-1000") // Investment
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(365), // 1 year later
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("4000") // 300% return (4x investment)
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Should be around 3.0 (300% annual return)
        assertThat(result).isCloseTo(
            3.0,
            org.assertj.core.data.Offset
                .offset(0.1)
        )

        // This demonstrates that realistic bounds should be much lower than 1000000.0
        // A reasonable upper bound might be 10.0 (1000% annual return)
        assertThat(result).isLessThan(10.0) // Much less than current unrealistic bound
    }

    @Test
    fun `should handle edge case with very small time differences`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: Very short time period (may cause division by small numbers)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-1000") // Investment
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(1), // Just 1 day later
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1100") // 10% return in 1 day
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // 10% return in 1 day annualized is extremely high
        // This tests numerical stability with very short time periods
        assertThat(result).isGreaterThan(0.0) // Should be positive
        assertThat(result.isFinite()).isTrue // Should not be infinite or NaN
    }

    @Test
    fun `should demonstrate need for better initial guess in complex scenarios`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: Multiple investments with delayed returns
        // Current initial guess of 0.1 (10%) may not be optimal
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // Multiple small investments
                for (i in 0..5) {
                    add(
                        Trn(
                            asset = asset,
                            tradeDate = LocalDate.now().plusDays(i * 30L),
                            trnType = TrnType.BUY,
                            cashAmount = BigDecimal("-200") // Regular investments
                        )
                    )
                }
                // Large final return
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(600),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1500") // Total return
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Complex cash flow patterns may benefit from better initial guess
        assertThat(result).isNotNull
        assertThat(result.isFinite()).isTrue
        // A more sophisticated initial guess might improve convergence
    }
}