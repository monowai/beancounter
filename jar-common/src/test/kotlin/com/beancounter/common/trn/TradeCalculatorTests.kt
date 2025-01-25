package com.beancounter.common.trn

import com.beancounter.common.input.TrnInput
import com.beancounter.common.utils.NumberUtils
import com.beancounter.common.utils.TradeCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Verify TradeCalculator assumptions based on the TrnInput supplied.
 */
class TradeCalculatorTests {
    private val tradeCalculator = TradeCalculator(NumberUtils())
    private val bigDecimalComparator = Comparator<BigDecimal> { o1, o2 -> o1.compareTo(o2) }

    @Test
    fun `tradeAmount Calculated when no tradeAmount`() {
        assertThat(
            tradeCalculator.amount(
                TrnInput(
                    quantity = 100.0.toBigDecimal(),
                    price = 1.0.toBigDecimal(),
                    fees = 10.0.toBigDecimal() // Added in
                )
            )
        ).usingComparator(bigDecimalComparator).isEqualTo(110.00.toBigDecimal())
    }

    @Test
    fun `tradeAmount Not Calculated when tradeAmount supplied`() {
        assertThat(
            tradeCalculator.amount(
                TrnInput(
                    tradeAmount = 2000.00.toBigDecimal(),
                    quantity = 100.0.toBigDecimal(),
                    price = 1.0.toBigDecimal(),
                    fees = 10.0.toBigDecimal() // Added in
                )
            )
        ).isEqualTo(2000.0.toBigDecimal()) // tradeAmount overrides calculated amounts
    }

    private val tradeAmount =
        tradeCalculator.amount(
            TrnInput(
                tradeAmount = 800.00.toBigDecimal()
            )
        )

    @Test
    fun `cash fxRates computed when no FxRate supplied`() {
        assertThat(
            tradeCalculator.cashFxRate(
                tradeAmount,
                TrnInput(
                    cashAmount = (-1600.0).toBigDecimal()
                )
            )
        ).usingComparator(bigDecimalComparator).isEqualTo(0.50.toBigDecimal())
    }

    @Test
    fun `override calculated cash rate by supplying trnInput`() {
        assertThat(
            tradeCalculator.cashFxRate(
                tradeAmount,
                TrnInput(
                    tradeCashRate = 0.60.toBigDecimal(),
                    cashAmount = (-1600.0).toBigDecimal()
                )
            )
        ).usingComparator(bigDecimalComparator).isEqualTo(0.60.toBigDecimal())
    }

    @Test
    fun `base fxRates computed when no FxRate supplied`() {
        assertThat(
            tradeCalculator.baseFxRate(
                tradeAmount,
                TrnInput(
                    cashAmount = tradeAmount
                )
            )
        ).usingComparator(bigDecimalComparator).isEqualTo(1.0.toBigDecimal())
    }

    @Test
    fun `override base rate by supplying trnInput baseRate`() {
        assertThat(
            tradeCalculator.baseFxRate(
                tradeAmount,
                TrnInput(
                    tradeBaseRate = 0.60.toBigDecimal(),
                    cashAmount = (-1600.0).toBigDecimal()
                )
            )
        ).usingComparator(bigDecimalComparator).isEqualTo(0.60.toBigDecimal())
    }
}