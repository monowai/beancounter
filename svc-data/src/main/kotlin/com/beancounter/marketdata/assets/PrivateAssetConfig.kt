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
    // Country code for tax jurisdiction (ISO 3166-1 alpha-2, e.g., "NZ", "SG")
    @Column(name = "country_code", length = 2)
    val countryCode: String = "NZ",
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
    // Tax settings - when true, income tax is deducted using the rate from Currency
    @Column(name = "deduct_income_tax")
    val deductIncomeTax: Boolean = false,
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
    // Pension settings
    @Column(name = "expected_return_rate", precision = 5, scale = 4)
    val expectedReturnRate: BigDecimal? = null,
    @Column(name = "payout_age")
    val payoutAge: Int? = null,
    @Column(name = "monthly_payout_amount", precision = 19, scale = 4)
    val monthlyPayoutAmount: BigDecimal? = null,
    @Column(name = "is_pension")
    val isPension: Boolean = false,
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
     * Calculate taxable income (rental minus all deductible expenses).
     * This is the income on which income tax is levied.
     * Cannot be negative (losses don't generate negative tax).
     */
    fun getTaxableIncome(): BigDecimal {
        val expenses = getTotalMonthlyExpenses()
        return monthlyRentalIncome.subtract(expenses).max(BigDecimal.ZERO)
    }

    /**
     * Calculate monthly income tax based on taxable income and the provided tax rate.
     * Tax is levied on profit (rent minus expenses), not gross rent.
     *
     * @param taxRate The income tax rate from the Currency (e.g., 0.20 for 20%)
     */
    fun getMonthlyIncomeTax(taxRate: BigDecimal): BigDecimal =
        if (deductIncomeTax) {
            getTaxableIncome()
                .multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

    /**
     * Calculate net monthly income after all expenses AND income tax.
     * This is the actual cash available for FI calculations.
     *
     * @param taxRate The income tax rate from the Currency (e.g., 0.20 for 20%)
     * Formula: Gross Rent - Expenses - Income Tax = Net Cash
     */
    fun getNetMonthlyIncome(taxRate: BigDecimal = BigDecimal.ZERO): BigDecimal =
        getTaxableIncome().subtract(getMonthlyIncomeTax(taxRate))

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