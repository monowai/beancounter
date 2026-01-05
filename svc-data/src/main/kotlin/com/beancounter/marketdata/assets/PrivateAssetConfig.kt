package com.beancounter.marketdata.assets

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Configuration for private assets (Real Estate, etc.).
 *
 * Stores income assumptions, expenses, and planning parameters that are specific to
 * user-owned assets. This is separate from the Asset entity to:
 * - Keep Asset entity clean and focused on core asset properties
 * - Allow optional configuration only for assets that need it
 * - Support extensibility for different private asset types
 *
 * Use cases:
 * - Real Estate: rental income, management fees, primary residence flag, liquidation priority
 * - Transaction generation: auto-create monthly income/expense transactions
 * - Could extend for: art/collectibles valuation frequency, insurance policies, etc.
 */
@Entity
@Table(name = "private_asset_config")
data class PrivateAssetConfig(
    @Id
    @Column(name = "asset_id")
    val assetId: String,
    // Income settings
    @Column(name = "monthly_rental_income", precision = 19, scale = 4)
    val monthlyRentalIncome: BigDecimal = BigDecimal.ZERO,
    @Column(name = "rental_currency", length = 3)
    val rentalCurrency: String = "NZD",
    // Expense settings - management
    @Column(name = "monthly_management_fee", precision = 19, scale = 4)
    val monthlyManagementFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "management_fee_percent", precision = 5, scale = 4)
    val managementFeePercent: BigDecimal = BigDecimal.ZERO,
    // Expense settings - property costs
    @Column(name = "monthly_body_corporate_fee", precision = 19, scale = 4)
    val monthlyBodyCorporateFee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "annual_property_tax", precision = 19, scale = 4)
    val annualPropertyTax: BigDecimal = BigDecimal.ZERO,
    @Column(name = "annual_insurance", precision = 19, scale = 4)
    val annualInsurance: BigDecimal = BigDecimal.ZERO,
    @Column(name = "monthly_other_expenses", precision = 19, scale = 4)
    val monthlyOtherExpenses: BigDecimal = BigDecimal.ZERO,
    // Planning settings
    @Column(name = "is_primary_residence")
    val isPrimaryResidence: Boolean = false,
    @Column(name = "liquidation_priority")
    val liquidationPriority: Int = 100,
    // Transaction generation settings
    @Column(name = "transaction_day_of_month")
    val transactionDayOfMonth: Int = 1,
    @Column(name = "credit_account_id")
    val creditAccountId: String? = null,
    @Column(name = "auto_generate_transactions")
    val autoGenerateTransactions: Boolean = false,
    // Timestamps
    @Column(name = "created_date", nullable = false)
    val createdDate: LocalDate = LocalDate.now(),
    @Column(name = "updated_date", nullable = false)
    val updatedDate: LocalDate = LocalDate.now()
) {
    companion object {
        private val TWELVE = BigDecimal(12)
    }

    /**
     * Calculate net monthly income (rental minus all expenses).
     * Management fee: uses either fixed fee or percentage of rental, whichever is greater.
     * Annual amounts (tax, insurance) are converted to monthly.
     */
    fun getNetMonthlyIncome(): BigDecimal {
        val percentFee = monthlyRentalIncome.multiply(managementFeePercent)
        val effectiveMgmtFee = monthlyManagementFee.max(percentFee)
        val monthlyTax = annualPropertyTax.divide(TWELVE, 4, RoundingMode.HALF_UP)
        val monthlyInsurance = annualInsurance.divide(TWELVE, 4, RoundingMode.HALF_UP)

        return monthlyRentalIncome
            .subtract(effectiveMgmtFee)
            .subtract(monthlyBodyCorporateFee)
            .subtract(monthlyTax)
            .subtract(monthlyInsurance)
            .subtract(monthlyOtherExpenses)
    }

    /**
     * Calculate total monthly expenses for this property.
     */
    fun getTotalMonthlyExpenses(): BigDecimal {
        val percentFee = monthlyRentalIncome.multiply(managementFeePercent)
        val effectiveMgmtFee = monthlyManagementFee.max(percentFee)
        val monthlyTax = annualPropertyTax.divide(TWELVE, 4, RoundingMode.HALF_UP)
        val monthlyInsurance = annualInsurance.divide(TWELVE, 4, RoundingMode.HALF_UP)

        return effectiveMgmtFee
            .add(monthlyBodyCorporateFee)
            .add(monthlyTax)
            .add(monthlyInsurance)
            .add(monthlyOtherExpenses)
    }
}