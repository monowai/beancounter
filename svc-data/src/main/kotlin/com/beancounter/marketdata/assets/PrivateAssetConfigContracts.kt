package com.beancounter.marketdata.assets

import java.math.BigDecimal

/**
 * Request to create or update a private asset configuration.
 */
data class PrivateAssetConfigRequest(
    // Income settings
    val monthlyRentalIncome: BigDecimal? = null,
    val rentalCurrency: String? = null,
    // Expense settings - management
    val monthlyManagementFee: BigDecimal? = null,
    val managementFeePercent: BigDecimal? = null,
    // Expense settings - property costs
    val monthlyBodyCorporateFee: BigDecimal? = null,
    val annualPropertyTax: BigDecimal? = null,
    val annualInsurance: BigDecimal? = null,
    val monthlyOtherExpenses: BigDecimal? = null,
    // Planning settings
    val isPrimaryResidence: Boolean? = null,
    val liquidationPriority: Int? = null,
    // Transaction generation settings
    val transactionDayOfMonth: Int? = null,
    val creditAccountId: String? = null,
    val autoGenerateTransactions: Boolean? = null
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