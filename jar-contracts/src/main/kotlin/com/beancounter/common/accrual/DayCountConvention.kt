package com.beancounter.common.accrual

/**
 * Day-count conventions used by [InterestAccrual] to translate calendar
 * intervals into year-fractions for compound-interest calculations.
 *
 * The current projection engine accumulates whole-year ticks (CPF sub-accounts
 * + lump-sum FV-of-annuity), so the choice of convention only matters for
 * partial-period accruals. We surface the convention now so the public
 * projection contract is honest about which rule produced the figures and
 * so future fractional-period work (mid-year contributions, daily
 * compounding) can switch the calculator without changing the call sites.
 *
 * Default for every BC projection is [ACT_365_FIXED] — the simplest fixed
 * 365-day year, which matches Singapore CPF's published rates and most
 * retail-savings-product literature.
 */
enum class DayCountConvention {
    /**
     * Actual days / 365. Fixed denominator regardless of leap years.
     * Most common retail-finance convention; matches CPF's stated rates
     * and what most insurance policy projections quote.
     */
    ACT_365_FIXED,

    /**
     * Actual days / 360. Common in money-market and short-dated USD
     * instruments. Produces a slightly higher year-fraction than ACT/365
     * for the same period (~1.4% larger).
     */
    ACT_360,

    /**
     * Actual days / actual days in year (365 or 366). Bond-market
     * convention; precise but expensive to compute. The projection engine
     * does not require this yet.
     */
    ACT_ACT,

    /**
     * 30 days per month, 360 days per year. Legacy US corporate bond
     * convention; included for completeness — not used by any BC
     * projection today.
     */
    THIRTY_360
}