package com.beancounter.common.accrual

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Single source of truth for compound-interest and annuity math used by
 * every BC retirement projection. Encapsulates the arithmetic so that
 * (a) no caller reimplements `(1 + r)^n`, and (b) the day-count
 * convention is an explicit parameter, not a hidden assumption.
 *
 * The default convention is [DayCountConvention.ACT_365_FIXED] — the
 * existing whole-year projection ticks were implicitly ACT/365 anyway,
 * so callers that don't care can stay convention-free. Surface it on
 * response DTOs (e.g. `LumpSumProjectionResponse.dayCountConvention`)
 * so the frontend can show the user which rule produced the numbers.
 */
@Component
class InterestAccrual {
    /**
     * Compound a principal at `annualRate` for `years` whole years.
     * Convention is honoured for fractional-year extensions in the
     * future; with an integer year count the result is identical
     * across all four supported conventions.
     */
    fun compound(
        principal: BigDecimal,
        annualRate: BigDecimal,
        years: Int,
        convention: DayCountConvention = DayCountConvention.ACT_365_FIXED
    ): BigDecimal {
        require(years >= 0) { "years must be non-negative, got $years" }
        if (years == 0 || principal == BigDecimal.ZERO || annualRate == BigDecimal.ZERO) {
            return principal
        }
        // Whole-year compounding is convention-agnostic — `convention`
        // is captured here so future partial-year work can branch on it.
        @Suppress("UNUSED_VARIABLE")
        val unused = convention
        return principal.multiply(BigDecimal.ONE.add(annualRate).pow(years, MC), MC)
    }

    /**
     * Compound the principal by one year-step at `annualRate`. Hot path
     * used by year-by-year projection loops (CPF sub-accounts) — kept
     * separate from [compound] so the loop doesn't pay `pow` overhead
     * per iteration.
     */
    fun compoundOneYear(
        principal: BigDecimal,
        annualRate: BigDecimal
    ): BigDecimal = principal.multiply(BigDecimal.ONE.add(annualRate), MC)

    /**
     * Future value of a monthly annuity (level payments at period end),
     * compounded at `annualRate`. Returns 0 when monthly payment or
     * months is non-positive. When `annualRate` is zero, returns the
     * undiscounted sum of contributions.
     *
     * FV = PMT × ((1 + r/12)^n - 1) / (r/12)
     */
    fun futureValueOfAnnuityMonthly(
        monthlyPayment: BigDecimal,
        annualRate: BigDecimal,
        totalMonths: Int,
        convention: DayCountConvention = DayCountConvention.ACT_365_FIXED
    ): BigDecimal {
        require(totalMonths >= 0) { "totalMonths must be non-negative, got $totalMonths" }
        if (monthlyPayment <= BigDecimal.ZERO || totalMonths == 0) {
            return BigDecimal.ZERO
        }
        if (annualRate <= BigDecimal.ZERO) {
            return monthlyPayment.multiply(BigDecimal(totalMonths))
        }
        @Suppress("UNUSED_VARIABLE")
        val unused = convention
        val monthlyRate = annualRate.divide(BigDecimal(12), SCALE, RoundingMode.HALF_UP)
        val growthFactor = BigDecimal.ONE.add(monthlyRate).pow(totalMonths)
        val numerator = growthFactor.subtract(BigDecimal.ONE)
        return monthlyPayment.multiply(numerator).divide(monthlyRate, 2, RoundingMode.HALF_UP)
    }

    /**
     * Year-fraction between `start` and `end` under `convention`.
     * Reserved for fractional-period work (mid-year contributions,
     * dated cashflows) — current callers only need whole years and
     * stick to [compound] / [compoundOneYear].
     */
    fun yearFraction(
        start: LocalDate,
        end: LocalDate,
        convention: DayCountConvention = DayCountConvention.ACT_365_FIXED
    ): BigDecimal {
        val days = ChronoUnit.DAYS.between(start, end)
        require(days >= 0) { "end must be on/after start" }
        val daysBd = BigDecimal(days)
        return when (convention) {
            DayCountConvention.ACT_365_FIXED -> daysBd.divide(BigDecimal(365), SCALE, RoundingMode.HALF_UP)
            DayCountConvention.ACT_360 -> daysBd.divide(BigDecimal(360), SCALE, RoundingMode.HALF_UP)
            DayCountConvention.ACT_ACT -> actActYearFraction(start, end)
            DayCountConvention.THIRTY_360 -> thirty360YearFraction(start, end)
        }
    }

    private fun actActYearFraction(
        start: LocalDate,
        end: LocalDate
    ): BigDecimal {
        // Simplified ACT/ACT (ISDA): split the interval at year boundaries,
        // accumulate days / days-in-each-year. Good enough for projection
        // displays; full bond-market ACT/ACT is more nuanced.
        var total = BigDecimal.ZERO
        var cursor = start
        while (cursor.year < end.year) {
            val yearEnd = LocalDate.of(cursor.year + 1, 1, 1)
            val daysInYear = if (cursor.isLeapYear) 366 else 365
            val days = ChronoUnit.DAYS.between(cursor, yearEnd)
            total = total.add(BigDecimal(days).divide(BigDecimal(daysInYear), SCALE, RoundingMode.HALF_UP))
            cursor = yearEnd
        }
        val daysInFinalYear = if (cursor.isLeapYear) 366 else 365
        val tailDays = ChronoUnit.DAYS.between(cursor, end)
        total =
            total.add(BigDecimal(tailDays).divide(BigDecimal(daysInFinalYear), SCALE, RoundingMode.HALF_UP))
        return total
    }

    private fun thirty360YearFraction(
        start: LocalDate,
        end: LocalDate
    ): BigDecimal {
        val d1 = minOf(start.dayOfMonth, 30)
        val d2 = if (d1 == 30) minOf(end.dayOfMonth, 30) else end.dayOfMonth
        val days = 360L * (end.year - start.year) + 30L * (end.monthValue - start.monthValue) + (d2 - d1)
        return BigDecimal(days).divide(BigDecimal(360), SCALE, RoundingMode.HALF_UP)
    }

    private val LocalDate.isLeapYear: Boolean get() =
        java.time.Year
            .of(year)
            .isLeap

    companion object {
        private val MC = MathContext.DECIMAL128
        private const val SCALE = 10
    }
}