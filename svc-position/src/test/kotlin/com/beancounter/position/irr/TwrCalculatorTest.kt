package com.beancounter.position.irr

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TwrCalculatorTest {
    private val calculator = TwrCalculator()

    @Test
    fun `empty snapshots returns zero`() {
        val result = calculator.calculate(emptyList())
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `single snapshot returns zero`() {
        val snapshots =
            listOf(
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 1),
                    marketValue = BigDecimal("10000")
                )
            )
        val result = calculator.calculate(snapshots)
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `simple growth without cash flows`() {
        // Portfolio grows from 10,000 to 11,000 = 10% return
        val snapshots =
            listOf(
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 1),
                    marketValue = BigDecimal("10000")
                ),
                ValuationSnapshot(
                    date = LocalDate.of(2024, 2, 1),
                    marketValue = BigDecimal("11000")
                )
            )
        val result = calculator.calculate(snapshots)
        assertThat(result.toDouble()).isCloseTo(0.10, Offset.offset(0.0001))
    }

    @Test
    fun `deposit does not inflate TWR`() {
        // Day 0: Value = 10,000
        // Day 15: Deposit 5,000. Value before deposit grew to 10,500 (5% growth).
        //         Value after deposit = 15,500. We record marketValue=15,500, CF=5,000.
        // Day 30: Value = 17,050 (10% growth on 15,500)
        //
        // Sub-period 1: (15,500 - 5,000) / 10,000 = 1.05 (5%)
        // Sub-period 2: 17,050 / 15,500 = 1.10 (10%)
        // TWR = 1.05 * 1.10 - 1 = 0.155 (15.5%)
        val snapshots =
            listOf(
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 1),
                    marketValue = BigDecimal("10000")
                ),
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 15),
                    marketValue = BigDecimal("15500"),
                    externalCashFlow = BigDecimal("5000")
                ),
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 30),
                    marketValue = BigDecimal("17050")
                )
            )
        val result = calculator.calculate(snapshots)
        assertThat(result.toDouble()).isCloseTo(0.155, Offset.offset(0.0001))
    }

    @Test
    fun `withdrawal does not deflate TWR`() {
        // Day 0: Value = 20,000
        // Day 15: Withdrawal 5,000. Value before withdrawal grew to 21,000 (5% growth).
        //         Value after withdrawal = 16,000. Record marketValue=16,000, CF=-5,000.
        // Day 30: Value = 17,600 (10% growth on 16,000)
        //
        // Sub-period 1: (16,000 - (-5,000)) / 20,000 = 21,000/20,000 = 1.05 (5%)
        // Sub-period 2: 17,600 / 16,000 = 1.10 (10%)
        // TWR = 1.05 * 1.10 - 1 = 0.155 (15.5%)
        val snapshots =
            listOf(
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 1),
                    marketValue = BigDecimal("20000")
                ),
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 15),
                    marketValue = BigDecimal("16000"),
                    externalCashFlow = BigDecimal("-5000")
                ),
                ValuationSnapshot(
                    date = LocalDate.of(2024, 1, 30),
                    marketValue = BigDecimal("17600")
                )
            )
        val result = calculator.calculate(snapshots)
        assertThat(result.toDouble()).isCloseTo(0.155, Offset.offset(0.0001))
    }

    @Test
    fun `deposit and withdrawal yield same TWR for same growth rates`() {
        // Both scenarios have 5% growth then 10% growth = TWR of 15.5%
        val depositSnapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal("10000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 15), BigDecimal("15500"), BigDecimal("5000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 30), BigDecimal("17050"))
            )
        val withdrawalSnapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal("20000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 15), BigDecimal("16000"), BigDecimal("-5000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 30), BigDecimal("17600"))
            )

        val depositTwr = calculator.calculate(depositSnapshots)
        val withdrawalTwr = calculator.calculate(withdrawalSnapshots)
        assertThat(depositTwr.toDouble()).isCloseTo(withdrawalTwr.toDouble(), Offset.offset(0.0001))
    }

    @Test
    fun `multiple cash flows chain correctly`() {
        // Three sub-periods: 5%, -2%, 8%
        // TWR = 1.05 * 0.98 * 1.08 - 1 = 0.11132 (11.132%)
        //
        // Day 0: Value = 10,000
        // Day 10: Deposit 3,000. Value before = 10,500 (5%). After = 13,500. CF=3,000.
        // Day 20: Withdrawal 2,000. Value before = 13,230 (-2% of 13,500). After = 11,230. CF=-2,000.
        // Day 30: Value = 12,128.40 (8% of 11,230)
        val snapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal("10000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 10), BigDecimal("13500"), BigDecimal("3000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 20), BigDecimal("11230"), BigDecimal("-2000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 30), BigDecimal("12128.40"))
            )
        val result = calculator.calculate(snapshots)
        val expected = 1.05 * 0.98 * 1.08 - 1.0 // = 0.11132
        assertThat(result.toDouble()).isCloseTo(expected, Offset.offset(0.001))
    }

    @Test
    fun `portfolio with loss`() {
        // Portfolio drops from 10,000 to 8,000 = -20% return
        val snapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal("10000")),
                ValuationSnapshot(LocalDate.of(2024, 2, 1), BigDecimal("8000"))
            )
        val result = calculator.calculate(snapshots)
        assertThat(result.toDouble()).isCloseTo(-0.20, Offset.offset(0.0001))
    }

    @Test
    fun `zero starting value skips first sub-period`() {
        // Day 0: Value = 0 (empty portfolio)
        // Day 1: Deposit 10,000. Value = 10,000. CF=10,000.
        // Day 30: Value = 10,500 (5% growth)
        //
        // First sub-period: (10,000 - 10,000) / 0 — start is 0, skip.
        // Second sub-period: 10,500 / 10,000 = 1.05 (5%)
        // TWR = 5%
        val snapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal.ZERO),
                ValuationSnapshot(LocalDate.of(2024, 1, 2), BigDecimal("10000"), BigDecimal("10000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 30), BigDecimal("10500"))
            )
        val result = calculator.calculate(snapshots)
        assertThat(result.toDouble()).isCloseTo(0.05, Offset.offset(0.0001))
    }

    @Test
    fun `growthOf1000 series is computed correctly`() {
        // 10% growth then 5% growth
        // Growth of 1000: 1000, 1100, 1155
        val snapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal("10000")),
                ValuationSnapshot(LocalDate.of(2024, 2, 1), BigDecimal("11000")),
                ValuationSnapshot(LocalDate.of(2024, 3, 1), BigDecimal("11550"))
            )
        val series = calculator.calculateSeries(snapshots)
        assertThat(series).hasSize(3)
        assertThat(series[0].toDouble()).isCloseTo(1000.0, Offset.offset(0.01))
        assertThat(series[1].toDouble()).isCloseTo(1100.0, Offset.offset(0.01))
        assertThat(series[2].toDouble()).isCloseTo(1155.0, Offset.offset(0.01))
    }

    @Test
    fun `growthOf1000 with deposit mid-period`() {
        // 5% growth, then deposit, then 10% growth
        // Growth of 1000: 1000, 1050, 1155
        val snapshots =
            listOf(
                ValuationSnapshot(LocalDate.of(2024, 1, 1), BigDecimal("10000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 15), BigDecimal("15500"), BigDecimal("5000")),
                ValuationSnapshot(LocalDate.of(2024, 1, 30), BigDecimal("17050"))
            )
        val series = calculator.calculateSeries(snapshots)
        assertThat(series).hasSize(3)
        assertThat(series[0].toDouble()).isCloseTo(1000.0, Offset.offset(0.01))
        assertThat(series[1].toDouble()).isCloseTo(1050.0, Offset.offset(0.01))
        assertThat(series[2].toDouble()).isCloseTo(1155.0, Offset.offset(0.01))
    }
}