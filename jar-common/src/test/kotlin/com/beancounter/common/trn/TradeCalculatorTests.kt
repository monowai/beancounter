package com.beancounter.common.trn

import com.beancounter.common.input.TrnInput
import com.beancounter.common.utils.NumberUtils
import com.beancounter.common.utils.TradeCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * BeanCounter will consistently _multiply_ a stored amount by the stored rate.
 * This class tests the TradeCalculator's ability to calculate the tradeAmount, cashFxRate, and baseFxRate
 * in the correct format
 */
class TradeCalculatorTests {
    private val tradeCalculator = TradeCalculator(NumberUtils())
    private val bigDecimalComparator = Comparator<BigDecimal> { o1, o2 -> o1.compareTo(o2) }

    @Test
    fun `tradeAmount Calculated when no tradeAmount`() {
        val input = TrnInput(quantity = 100.0.toBigDecimal(), price = 1.0.toBigDecimal(), fees = 10.0.toBigDecimal())
        assertThat(tradeCalculator.amount(input)).usingComparator(bigDecimalComparator).isEqualTo(110.00.toBigDecimal())
    }

    @Test
    fun `tradeAmount Not Calculated when tradeAmount supplied`() {
        val input =
            TrnInput(
                tradeAmount = 2000.00.toBigDecimal(),
                quantity = 100.0.toBigDecimal(),
                price = 1.0.toBigDecimal(),
                fees = 10.0.toBigDecimal()
            )
        assertThat(tradeCalculator.amount(input)).isEqualTo(2000.0.toBigDecimal())
    }

    private val tradeAmount = tradeCalculator.amount(TrnInput(tradeAmount = 800.00.toBigDecimal()))

    @Test
    fun `cash fxRates computed when no FxRate supplied`() {
        val input = TrnInput(cashAmount = (-1600.0).toBigDecimal())
        assertThat(tradeCalculator.cashFxRate(tradeAmount, input))
            .usingComparator(bigDecimalComparator)
            .isEqualTo(2.00.toBigDecimal())
    }

    @Test
    fun `override calculated cash rate by supplying trnInput`() {
        val input = TrnInput(tradeCashRate = 2.10.toBigDecimal(), cashAmount = (-1600.0).toBigDecimal())
        assertThat(tradeCalculator.cashFxRate(tradeAmount, input))
            .usingComparator(bigDecimalComparator)
            .isEqualTo(2.10.toBigDecimal()) // Rate is set in TrnInput.
    }

    @Test
    fun `base fxRates computed when no FxRate supplied`() {
        val input = TrnInput(cashAmount = tradeAmount)
        assertThat(tradeCalculator.baseFxRate(tradeAmount, input))
            .usingComparator(bigDecimalComparator)
            .isEqualTo(1.0.toBigDecimal())
    }

    @Test
    fun `override base rate by supplying trnInput baseRate`() {
        val input = TrnInput(tradeBaseRate = 0.60.toBigDecimal(), cashAmount = (-1600.0).toBigDecimal())
        assertThat(tradeCalculator.baseFxRate(tradeAmount, input))
            .usingComparator(bigDecimalComparator)
            .isEqualTo(0.60.toBigDecimal())
    }
}