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
}