package com.beancounter.position.irr

import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.US
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.LocalDate

class IrrCalculatorSimpleRoiTest {
    private val asset = AssetUtils.getTestAsset(US, "TestAsset")

    @Test
    fun `calculateSimpleROI is called when holding period is less than custom minHoldingDays`() {
        val customMinHoldingDays = 500
        val irrCalculator = spy(IrrCalculator(minHoldingDays = customMinHoldingDays, dateUtils = DateUtils()))
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal(-1000)
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(10),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal(1100)
                    )
                )
            }

        irrCalculator.calculate(periodicCashFlows)

        verify(irrCalculator).calculateSimpleRoi(periodicCashFlows)
    }

    @Test
    fun `should calculate correct Simple ROI percentage for multiple cash flows scenario`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 10, dateUtils = DateUtils())

        // Multiple investments: $1000 + $500 = $1500 total, return $1800
        // Expected ROI: (1800 - 1500) / 1500 = 0.2 (20%)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal(-1000)
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(2),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal(-500)
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(5),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal(1800)
                    )
                )
            }

        val result = irrCalculator.calculateSimpleRoi(periodicCashFlows)

        assertThat(result).isEqualTo(0.2)
    }

    @Test
    fun `should calculate correct Simple ROI percentage for losing investment`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 10, dateUtils = DateUtils())

        // Loss scenario: invest $1000, return $800 = -20% ROI
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal(-1000)
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(5),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal(800)
                    )
                )
            }

        val result = irrCalculator.calculateSimpleRoi(periodicCashFlows)

        assertThat(result).isEqualTo(-0.2)
    }

    @Test
    fun `should handle zero cash flow validation correctly`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Zero initial investment should trigger validation logic
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal.ZERO
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(600),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1000")
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `should handle high precision amounts without errors`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // High precision amounts test numerical stability
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-1000.123456789012345")
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(600),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("1100.987654321098765")
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        assertThat(result).isNotNull
    }
}