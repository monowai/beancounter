package com.beancounter.marketdata.assets

/**
 * Cadence of regular contributions to a pension/policy asset. Stored on the
 * asset config so a Work Scenario contribution amount can be annualised
 * (MONTHLY × 12, ANNUAL × 1) during retirement projection without each
 * scenario picking its own frequency.
 */
enum class ContributionFrequency {
    MONTHLY,
    ANNUAL
}