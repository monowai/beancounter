package com.beancounter.common.accrual

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class InterestAccrualTest {
    private val accrual = InterestAccrual()

    @Test
    fun `compound at zero years returns principal unchanged`() {
        val result = accrual.compound(BigDecimal(1000), BigDecimal("0.04"), 0)
        assertThat(result).isEqualByComparingTo(BigDecimal(1000))
    }

    @Test
    fun `compound applies annual rate over whole years`() {
        // 1000 @ 4% for 10 years = 1000 * 1.04^10 ≈ 1480.244
        val result = accrual.compound(BigDecimal(1000), BigDecimal("0.04"), 10)
        assertThat(result.setScale(2, RoundingMode.HALF_UP))
            .isEqualByComparingTo(BigDecimal("1480.24"))
    }

    @Test
    fun `compoundOneYear matches single compound step`() {
        val oneStep = accrual.compoundOneYear(BigDecimal(1000), BigDecimal("0.04"))
        val viaCompound = accrual.compound(BigDecimal(1000), BigDecimal("0.04"), 1)
        assertThat(oneStep.setScale(4, RoundingMode.HALF_UP))
            .isEqualByComparingTo(viaCompound.setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `futureValueOfAnnuityMonthly applies FV-of-annuity formula`() {
        // PMT=100, r=0.06/yr, n=12 months → FV = 100 * ((1.005)^12 - 1) / 0.005 ≈ 1233.56
        val result =
            accrual.futureValueOfAnnuityMonthly(
                monthlyPayment = BigDecimal(100),
                annualRate = BigDecimal("0.06"),
                totalMonths = 12
            )
        assertThat(result).isEqualByComparingTo(BigDecimal("1233.56"))
    }

    @Test
    fun `futureValueOfAnnuityMonthly with zero rate returns plain sum of contributions`() {
        val result =
            accrual.futureValueOfAnnuityMonthly(
                monthlyPayment = BigDecimal(100),
                annualRate = BigDecimal.ZERO,
                totalMonths = 24
            )
        assertThat(result).isEqualByComparingTo(BigDecimal(2400))
    }

    @Test
    fun `futureValueOfAnnuityMonthly with zero months returns zero`() {
        val result =
            accrual.futureValueOfAnnuityMonthly(
                monthlyPayment = BigDecimal(100),
                annualRate = BigDecimal("0.05"),
                totalMonths = 0
            )
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `negative years rejected`() {
        assertThrows<IllegalArgumentException> {
            accrual.compound(BigDecimal(1000), BigDecimal("0.04"), -1)
        }
    }

    @Test
    fun `ACT_365_FIXED year-fraction over one calendar year is exactly 1`() {
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                DayCountConvention.ACT_365_FIXED
            )
        // 365 days / 365 = 1.0
        assertThat(yf).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `ACT_360 year-fraction over 365 days is greater than 1`() {
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                DayCountConvention.ACT_360
            )
        // 365 / 360 ≈ 1.0139
        assertThat(yf).isGreaterThan(BigDecimal.ONE)
    }

    @Test
    fun `THIRTY_360 year-fraction over exact calendar year is 1`() {
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                DayCountConvention.THIRTY_360
            )
        assertThat(yf).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `THIRTY_360 year-fraction handles mid-month dates via standard formula`() {
        // 30/360 between 2025-03-15 and 2025-09-15:
        // days = 360*0 + 30*(9-3) + (15-15) = 180  →  180/360 = 0.5
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 3, 15),
                LocalDate.of(2025, 9, 15),
                DayCountConvention.THIRTY_360
            )
        assertThat(yf).isEqualByComparingTo(BigDecimal("0.5"))
    }

    @Test
    fun `ACT_ACT year-fraction over non-leap calendar year is 1`() {
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                DayCountConvention.ACT_ACT
            )
        // 365 / 365 (2025 not a leap year) = 1
        assertThat(yf).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `ACT_ACT year-fraction over a leap year is 1`() {
        // 2024 = 366 days. ACT/ACT splits at year boundary so still produces 1.
        val yf =
            accrual.yearFraction(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1),
                DayCountConvention.ACT_ACT
            )
        assertThat(yf).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `ACT_ACT year-fraction spanning two years sums each portion correctly`() {
        // Mid-2024 (leap, from Jul 1) → Mid-2025 (non-leap, to Jul 1):
        // 2024-07-01 → 2025-01-01 = 184 days / 366 ≈ 0.5027
        // 2025-01-01 → 2025-07-01 = 181 days / 365 ≈ 0.4959
        // total ≈ 0.9986
        val yf =
            accrual.yearFraction(
                LocalDate.of(2024, 7, 1),
                LocalDate.of(2025, 7, 1),
                DayCountConvention.ACT_ACT
            )
        assertThat(yf.toDouble()).isCloseTo(
            0.9986,
            org.assertj.core.data.Offset
                .offset(0.001)
        )
    }

    @Test
    fun `ACT_ACT year-fraction sub-year interval inside a single year`() {
        // Q1 2025 = 90 days / 365 ≈ 0.2466
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 4, 1),
                DayCountConvention.ACT_ACT
            )
        assertThat(yf.toDouble()).isCloseTo(
            90.0 / 365.0,
            org.assertj.core.data.Offset
                .offset(0.0001)
        )
    }

    @Test
    fun `yearFraction same start and end returns zero`() {
        val sameDay = LocalDate.of(2025, 6, 15)
        assertThat(accrual.yearFraction(sameDay, sameDay, DayCountConvention.ACT_365_FIXED))
            .isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(accrual.yearFraction(sameDay, sameDay, DayCountConvention.ACT_ACT))
            .isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(accrual.yearFraction(sameDay, sameDay, DayCountConvention.THIRTY_360))
            .isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `yearFraction with end before start is rejected`() {
        assertThrows<IllegalArgumentException> {
            accrual.yearFraction(
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 1, 1)
            )
        }
    }

    @Test
    fun `yearFraction default convention is ACT_365_FIXED`() {
        // Default-arg path: caller passes only the two dates.
        val explicit =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                DayCountConvention.ACT_365_FIXED
            )
        val defaulted =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1)
            )
        assertThat(defaulted).isEqualByComparingTo(explicit)
    }

    @Test
    fun `compound default convention is ACT_365_FIXED`() {
        val explicit = accrual.compound(BigDecimal(1000), BigDecimal("0.04"), 5, DayCountConvention.ACT_365_FIXED)
        val defaulted = accrual.compound(BigDecimal(1000), BigDecimal("0.04"), 5)
        assertThat(defaulted).isEqualByComparingTo(explicit)
    }

    @Test
    fun `futureValueOfAnnuityMonthly default convention is ACT_365_FIXED`() {
        val explicit =
            accrual.futureValueOfAnnuityMonthly(
                BigDecimal(100),
                BigDecimal("0.06"),
                12,
                DayCountConvention.ACT_365_FIXED
            )
        val defaulted = accrual.futureValueOfAnnuityMonthly(BigDecimal(100), BigDecimal("0.06"), 12)
        assertThat(defaulted).isEqualByComparingTo(explicit)
    }

    @Test
    fun `compound returns principal unchanged when principal is zero`() {
        val result = accrual.compound(BigDecimal.ZERO, BigDecimal("0.05"), 10)
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `compound returns principal unchanged when rate is zero`() {
        val result = accrual.compound(BigDecimal(1000), BigDecimal.ZERO, 10)
        assertThat(result).isEqualByComparingTo(BigDecimal(1000))
    }

    @Test
    fun `compoundOneYear with zero rate returns principal unchanged`() {
        val result = accrual.compoundOneYear(BigDecimal(1000), BigDecimal.ZERO)
        assertThat(result).isEqualByComparingTo(BigDecimal(1000))
    }

    @Test
    fun `futureValueOfAnnuityMonthly with negative monthly payment returns zero`() {
        // Defensive guard — projection inputs are user-driven and a
        // negative figure shouldn't silently compound into a negative
        // future value.
        val result =
            accrual.futureValueOfAnnuityMonthly(
                monthlyPayment = BigDecimal(-100),
                annualRate = BigDecimal("0.05"),
                totalMonths = 24
            )
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `futureValueOfAnnuityMonthly with negative months rejected`() {
        assertThrows<IllegalArgumentException> {
            accrual.futureValueOfAnnuityMonthly(BigDecimal(100), BigDecimal("0.05"), -1)
        }
    }

    @Test
    fun `compound for many years produces large but finite values`() {
        // 1000 @ 5% for 100 years ≈ 131,501 — guard against overflow /
        // precision loss with DECIMAL128.
        val result = accrual.compound(BigDecimal(1000), BigDecimal("0.05"), 100)
        assertThat(result.toDouble()).isCloseTo(
            131501.26,
            org.assertj.core.data.Offset
                .offset(1.0)
        )
    }

    @Test
    fun `compound chained one-year matches single multi-year call`() {
        // Five single-year ticks must equal one 5-year compound. Sanity
        // check that callers can interleave compoundOneYear inside a
        // projection loop without diverging from the closed-form value.
        var stepwise = BigDecimal(1000)
        repeat(5) { stepwise = accrual.compoundOneYear(stepwise, BigDecimal("0.04")) }
        val direct = accrual.compound(BigDecimal(1000), BigDecimal("0.04"), 5)
        assertThat(stepwise.setScale(4, RoundingMode.HALF_UP))
            .isEqualByComparingTo(direct.setScale(4, RoundingMode.HALF_UP))
    }

    @Test
    fun `ACT_ACT year-fraction inside a leap year is divided by 366`() {
        // Same calendar year, leap: tailDays > 0 AND cursor.isLeapYear == true.
        // Drives the final-year leap branch in actActYearFraction (line 125).
        // Q1 2024 has 91 days (Jan 31 + Feb 29 + Mar 31). 91 / 366 ≈ 0.2486.
        val yf =
            accrual.yearFraction(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 4, 1),
                DayCountConvention.ACT_ACT
            )
        assertThat(yf.toDouble()).isCloseTo(
            91.0 / 366.0,
            org.assertj.core.data.Offset
                .offset(0.0001)
        )
    }

    @Test
    fun `ACT_ACT year-fraction spanning three years exercises both isLeapYear branches`() {
        // 2023 (non-leap, partial), 2024 (leap, full), 2025 (non-leap, partial).
        // Forces the while-loop to iterate twice and the final-year branch
        // to fire — exercises cursor.isLeapYear on both leap and non-leap.
        val yf =
            accrual.yearFraction(
                LocalDate.of(2023, 7, 1),
                LocalDate.of(2025, 7, 1),
                DayCountConvention.ACT_ACT
            )
        // 2023-07-01 → 2024-01-01 = 184 / 365 ≈ 0.5041
        // 2024-01-01 → 2025-01-01 = 366 / 366 = 1.0
        // 2025-01-01 → 2025-07-01 = 181 / 365 ≈ 0.4959
        // total ≈ 2.0000
        assertThat(yf.toDouble()).isCloseTo(
            2.0,
            org.assertj.core.data.Offset
                .offset(0.001)
        )
    }

    @Test
    fun `THIRTY_360 year-fraction caps both endpoints at day 30`() {
        // start day 31 → d1 caps to 30; end day 31 → d2 caps to 30 (because d1==30 triggers cap).
        // 2025-01-31 → 2025-12-31 under 30/360:
        //   360*0 + 30*(12-1) + (30 - 30) = 330  →  330/360 ≈ 0.9167
        val yf =
            accrual.yearFraction(
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 12, 31),
                DayCountConvention.THIRTY_360
            )
        assertThat(yf.toDouble()).isCloseTo(
            330.0 / 360.0,
            org.assertj.core.data.Offset
                .offset(0.0001)
        )
    }

    @Test
    fun `DayCountConvention enum exposes all four supported rules`() {
        // Quick guard so a future "drop a convention to save a switch
        // arm" refactor surfaces a test failure rather than a silent
        // behaviour change.
        assertThat(DayCountConvention.entries).containsExactly(
            DayCountConvention.ACT_365_FIXED,
            DayCountConvention.ACT_360,
            DayCountConvention.ACT_ACT,
            DayCountConvention.THIRTY_360
        )
    }
}