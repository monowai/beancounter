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

class IrrCalculatorAnnualizedGuessTest {
    private val asset = AssetUtils.getTestAsset(US, "AnnualizedGuessTestAsset")

    @Test
    fun `should use annualized Simple ROI as initial guess for long holding periods`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: 300% return over 10 years (as mentioned in the user's example)
        // Simple ROI = 3.0, but annualized should be (1 + 3.0)^(1/10) - 1 ≈ 0.148 (14.8%)
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
                        tradeDate = LocalDate.now().plusDays(3650), // 10 years later
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("4000") // 300% return (4x investment)
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Expected annualized IRR should be around 14.8% for 300% over 10 years
        assertThat(result).isCloseTo(
            0.148,
            org.assertj.core.data.Offset
                .offset(0.01)
        )

        // Verify it's significantly different from the simple ROI of 3.0
        assertThat(result).isLessThan(1.0) // Much less than 300% total return
        assertThat(result).isGreaterThan(0.1) // But still a healthy annual return
    }

    @Test
    fun `should handle short holding periods without annualization issues`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: 20% return over 1 year - annualization should have minimal impact
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
                        cashAmount = BigDecimal("1200") // 20% return
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Should be close to 20% annual return
        assertThat(result).isCloseTo(
            0.2,
            org.assertj.core.data.Offset
                .offset(0.02)
        )
    }

    @Test
    fun `should handle moderate holding periods with appropriate annualization`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Scenario: 100% return over 2 years
        // Simple ROI = 1.0, annualized = (1 + 1.0)^(1/2) - 1 ≈ 0.414 (41.4%)
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
                        tradeDate = LocalDate.now().plusDays(730), // 2 years later
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("2000") // 100% return (2x investment)
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Expected annualized IRR should be around 41.4% for 100% over 2 years
        assertThat(result).isCloseTo(
            0.414,
            org.assertj.core.data.Offset
                .offset(0.02)
        )
    }

    @Test
    fun `should handle edge case with zero holding period`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils()) // Force XIRR calculation

        // Edge case: same-day buy and sell (should fallback to simple ROI)
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
                        tradeDate = LocalDate.now(), // Same day
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1100") // 10% return
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Should handle gracefully without division by zero
        assertThat(result).isNotNull
        assertThat(result.isFinite()).isTrue
    }
}