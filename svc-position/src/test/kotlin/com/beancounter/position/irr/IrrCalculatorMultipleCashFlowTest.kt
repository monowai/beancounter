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

class IrrCalculatorMultipleCashFlowTest {
    private val asset = AssetUtils.getTestAsset(US, "MultipleCashFlowTestAsset")

    @Test
    fun `should handle dollar-cost averaging scenario accurately`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario: Regular monthly investments over 2 years, then sell
        // $500 monthly investment, final value $15,000 (vs $12,000 invested)
        val baseDate = LocalDate.of(2022, 1, 1)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // 24 monthly investments of $500 each
                for (month in 0..23) {
                    add(
                        Trn(
                            asset = asset,
                            tradeDate = baseDate.plusMonths(month.toLong()),
                            trnType = TrnType.BUY,
                            cashAmount = BigDecimal("-500")
                        )
                    )
                }
                // Final sale after 2 years
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusYears(2),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("15000") // 25% total gain
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // For dollar-cost averaging, the IRR is higher than simple annualized return
        // because later investments have less time to grow, creating a timing advantage
        // Expected IRR: approximately 23% annually due to cash flow timing
        assertThat(result).isCloseTo(
            0.23,
            org.assertj.core.data.Offset
                .offset(0.03)
        )
    }

    @Test
    fun `should handle dividend reinvestment with precise timing`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario: Initial investment, quarterly dividends reinvested, final sale
        val baseDate = LocalDate.of(2023, 1, 15)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // Initial investment
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate,
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-10000")
                    )
                )

                // Quarterly dividend reinvestments (2% quarterly dividend)
                for (quarter in 1..8) { // 2 years of dividends
                    add(
                        Trn(
                            asset = asset,
                            tradeDate = baseDate.plusMonths(quarter * 3L),
                            trnType = TrnType.BUY,
                            cashAmount = BigDecimal("-200") // 2% dividend reinvested
                        )
                    )
                }

                // Final sale at 50% appreciation plus dividends
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusYears(2),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("16800") // $15,000 principal + $1,600 dividends + 12% growth
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Should reflect both capital appreciation and dividend compounding
        assertThat(result).isGreaterThan(0.10) // At least 10% annually
        assertThat(result).isLessThan(0.25) // Less than 25% annually
    }

    @Test
    fun `should handle partial selling with remaining position`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario: Buy, partial sales over time, final liquidation
        val baseDate = LocalDate.of(2023, 6, 1)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // Large initial investment
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate,
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-20000")
                    )
                )

                // Partial sale after 6 months (25% of position)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusMonths(6),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("6000") // 20% gain on partial sale
                    )
                )

                // Another partial sale after 1 year (25% of original)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusYears(1),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("6500") // Further appreciation
                    )
                )

                // Final liquidation after 18 months (remaining 50%)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusMonths(18),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("14000") // Remaining position with good gains
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Total cash in: $20,000, Total cash out: $26,500 over 18 months
        // Should reflect the staggered realization of gains
        assertThat(result).isGreaterThan(0.15) // At least 15% annually
        assertThat(result).isLessThan(0.35) // Less than 35% annually
    }

    @Test
    fun `should handle complex multi-year investment pattern`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario: Variable investments, some losses, ultimate recovery
        val baseDate = LocalDate.of(2020, 3, 1) // Start during market volatility
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // Initial large investment
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate,
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-15000")
                    )
                )

                // Panic sell during downturn (6 months later)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusMonths(6),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("9000") // 40% loss
                    )
                )

                // Buy back at better prices (3 months later)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusMonths(9),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-12000")
                    )
                )

                // Additional investment during recovery
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusMonths(15),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-5000")
                    )
                )

                // Final sale after strong recovery (3 years total)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusYears(3),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("35000") // Strong final recovery
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Net: Invested $32,000, received $44,000 over 3 years
        // Should handle the complex timing of losses and recovery
        assertThat(result).isNotNull
        assertThat(result.isFinite()).isTrue
        assertThat(result).isGreaterThan(0.05) // Some positive return despite volatility
    }

    @Test
    fun `should handle leap year precision in multi-year calculations`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario: Investment spanning leap year boundary for timing precision test
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // Start just before leap year
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.of(2023, 12, 31),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-10000")
                    )
                )

                // End after leap year (Feb 29, 2024 exists)
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.of(2024, 12, 31),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("12000") // 20% return
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Should precisely handle the 366 days of 2024 (leap year)
        // Expected: approximately 20% annual return
        assertThat(result).isCloseTo(
            0.20,
            org.assertj.core.data.Offset
                .offset(0.01)
        )
    }

    @Test
    fun `should handle high-frequency trading with precise timing`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario: Frequent small trades testing timing precision
        val baseDate = LocalDate.of(2023, 1, 1)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                var currentAmount = 1000.0

                // Series of weekly trades with small gains
                for (week in 0..103) { // 2 years of weekly trading
                    val isInvestment = week % 4 == 0 // Invest every 4th week
                    val amount =
                        if (isInvestment) {
                            -currentAmount * 0.1 // Invest 10% more
                        } else {
                            currentAmount * 0.02 // Small profit taking
                        }

                    add(
                        Trn(
                            asset = asset,
                            tradeDate = baseDate.plusWeeks(week.toLong()),
                            trnType = if (isInvestment) TrnType.BUY else TrnType.SELL,
                            cashAmount = BigDecimal(amount)
                        )
                    )

                    currentAmount = if (isInvestment) currentAmount * 1.1 else currentAmount * 1.02
                }

                // Final liquidation
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusWeeks(104),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal(currentAmount * 1.5) // Final profit
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // Should handle the complex frequent cash flow pattern
        assertThat(result).isNotNull
        assertThat(result.isFinite()).isTrue
    }

    @Test
    fun `should demonstrate improved convergence with annualized guess for complex scenarios`() {
        val irrCalculator = IrrCalculator(minHoldingDays = 500, dateUtils = DateUtils())

        // Scenario designed to test solver convergence with better initial guess
        val baseDate = LocalDate.of(2019, 1, 1)
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                // Large initial investment
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate,
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal("-100000")
                    )
                )

                // Series of smaller additional investments
                for (year in 1..4) {
                    add(
                        Trn(
                            asset = asset,
                            tradeDate = baseDate.plusYears(year.toLong()),
                            trnType = TrnType.BUY,
                            cashAmount = BigDecimal("-20000")
                        )
                    )
                }

                // Final sale after 5 years with substantial gains
                add(
                    Trn(
                        asset = asset,
                        tradeDate = baseDate.plusYears(5),
                        trnType = TrnType.SELL,
                        cashAmount = BigDecimal("300000") // Total: $180k in, $300k out
                    )
                )
            }

        val result = irrCalculator.calculate(periodicCashFlows)

        // The annualized initial guess should help convergence for this scenario
        // Total gain: $120k on $180k over 5 years with staggered investments
        assertThat(result).isGreaterThan(0.08) // At least 8% annually
        assertThat(result).isLessThan(0.20) // Less than 20% annually
        assertThat(result.isFinite()).isTrue
    }
}