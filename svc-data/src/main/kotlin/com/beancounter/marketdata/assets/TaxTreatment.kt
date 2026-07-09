package com.beancounter.marketdata.assets

/**
 * Tax treatment applied to a retirement/pension account's contributions and
 * withdrawals.
 *
 * - TRADITIONAL: pre-tax contributions; withdrawals taxed as income
 *   (US 401k/IRA "Traditional").
 * - ROTH: post-tax contributions; qualified withdrawals tax-free
 *   (US Roth 401k/IRA).
 * - TAX_FREE: contributions and growth both tax-free (UK ISA).
 *
 * Kept as its own column rather than folded into [PolicyType] because policy
 * type doesn't uniquely determine tax treatment — e.g. a US_401K can be
 * either TRADITIONAL or ROTH. Persisted with `@Enumerated(EnumType.STRING)`,
 * mirroring how [PolicyType] is stored on [PrivateAssetConfig].
 */
enum class TaxTreatment {
    TRADITIONAL,
    ROTH,
    TAX_FREE
}