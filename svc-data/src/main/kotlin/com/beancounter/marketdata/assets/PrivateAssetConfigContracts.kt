package com.beancounter.marketdata.assets

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request to create or update a private asset configuration.
 */
data class PrivateAssetConfigRequest(
    // Income settings
    val monthlyRentalIncome: BigDecimal? = null,
    val rentalCurrency: String? = null,
    // Country code for tax jurisdiction (ISO 3166-1 alpha-2, e.g., "NZ", "SG")
    val countryCode: String? = null,
    // Expense settings - management
    val monthlyManagementFee: BigDecimal? = null,
    val managementFeePercent: BigDecimal? = null,
    // Expense settings - property costs
    val monthlyBodyCorporateFee: BigDecimal? = null,
    val annualPropertyTax: BigDecimal? = null,
    val annualInsurance: BigDecimal? = null,
    val monthlyOtherExpenses: BigDecimal? = null,
    // Tax settings - when true, deduct income tax using rate from Currency
    val deductIncomeTax: Boolean? = null,
    // Planning settings
    val isPrimaryResidence: Boolean? = null,
    val liquidationPriority: Int? = null,
    // Transaction generation settings
    val transactionDayOfMonth: Int? = null,
    val creditAccountId: String? = null,
    val autoGenerateTransactions: Boolean? = null,
    // Pension/Policy payout settings
    val expectedReturnRate: BigDecimal? = null,
    val payoutAge: Int? = null,
    val monthlyPayoutAmount: BigDecimal? = null,
    val lumpSum: Boolean? = null,
    // Regular contribution amount (e.g., pension contributions, insurance premiums)
    val monthlyContribution: BigDecimal? = null,
    val isPension: Boolean? = null,
    // Composite policy support
    val policyType: PolicyType? = null,
    val lockedUntilDate: LocalDate? = null,
    // CPF-specific settings
    val cpfLifePlan: String? = null,
    val cpfPayoutStartAge: Int? = null,
    val subAccounts: List<SubAccountRequest>? = null
)

/**
 * Request to create or update a sub-account within a composite policy asset.
 */
data class SubAccountRequest(
    val code: String,
    val displayName: String? = null,
    val balance: BigDecimal = BigDecimal.ZERO,
    val expectedReturnRate: BigDecimal? = null,
    val feeRate: BigDecimal? = null,
    val liquid: Boolean = true
)

/**
 * Response wrapper for a single private asset configuration.
 */
data class PrivateAssetConfigResponse(
    val data: PrivateAssetConfig
)

/**
 * Response wrapper for multiple private asset configurations.
 */
data class PrivateAssetConfigsResponse(
    val data: Collection<PrivateAssetConfig>
)