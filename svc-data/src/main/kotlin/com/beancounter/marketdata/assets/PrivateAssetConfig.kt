package com.beancounter.marketdata.assets

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
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
    // Pension/Policy payout settings
    @Column(name = "expected_return_rate", precision = 5, scale = 4)
    val expectedReturnRate: BigDecimal? = null,
    @Column(name = "payout_age")
    val payoutAge: Int? = null,
    @Column(name = "monthly_payout_amount", precision = 19, scale = 4)
    val monthlyPayoutAmount: BigDecimal? = null,
    @Column(name = "lump_sum")
    val lumpSum: Boolean = false,
    // Regular contribution amount (e.g., pension contributions, insurance premiums)
    @Column(name = "monthly_contribution", precision = 19, scale = 4)
    val monthlyContribution: BigDecimal? = null,
    // Cadence of pension contributions captured against this asset; the
    // Work Scenario stores the amount, this resolves how it annualises.
    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_frequency", length = 10, nullable = false)
    val contributionFrequency: ContributionFrequency = ContributionFrequency.MONTHLY,
    @Column(name = "is_pension")
    val isPension: Boolean = false,
    // Composite policy support
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", length = 20)
    val policyType: PolicyType? = null,
    @Column(name = "locked_until_date")
    val lockedUntilDate: LocalDate? = null,
    // CPF-specific settings (only relevant when policyType = CPF)
    @Column(name = "cpf_life_plan", length = 20)
    val cpfLifePlan: String? = null,
    @Column(name = "cpf_payout_start_age")
    val cpfPayoutStartAge: Int? = null,
    // US 401k/IRA + UK ISA specific settings (relevant when policyType is
    // US_401K, US_IRA or UK_ISA, but kept permissive for other types — MVP).
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", length = 20)
    val taxTreatment: TaxTreatment? = null,
    // % of gross salary deferred into the account (e.g. 0.0600 = 6%)
    @Column(name = "employee_deferral_percent", precision = 5, scale = 4)
    val employeeDeferralPercent: BigDecimal? = null,
    // Employer match as a % of salary (e.g. 0.5000 = 50c per $1 deferred)
    @Column(name = "employer_match_percent", precision = 5, scale = 4)
    val employerMatchPercent: BigDecimal? = null,
    // Salary % cap on the employer match (e.g. 0.0600 = match capped at 6% of salary)
    @Column(name = "employer_match_cap_percent", precision = 5, scale = 4)
    val employerMatchCapPercent: BigDecimal? = null,
    // Effective flat tax rate applied to TRADITIONAL withdrawals at projection time
    @Column(name = "withdrawal_tax_rate", precision = 5, scale = 4)
    val withdrawalTaxRate: BigDecimal? = null,
    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    // Read-only join: each PrivateAssetSubAccount owns and writes its own
    // (NOT NULL) `asset_id`. Marking the collection's join column
    // insertable/updatable=false stops Hibernate's unidirectional-@OneToMany
    // wart of issuing `UPDATE ... SET asset_id = NULL` before deleting children
    // — that null-UPDATE violated the NOT NULL constraint and blocked deletes.
    // cascade=ALL + orphanRemoval still DELETE the child rows directly.
    @JoinColumn(
        name = "asset_id",
        referencedColumnName = "asset_id",
        insertable = false,
        updatable = false
    )
    val subAccounts: MutableList<PrivateAssetSubAccount> = mutableListOf(),
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
     * A composite asset has sub-accounts (e.g. CPF, ILP).
     * Simple assets have no sub-accounts and use the existing single-bucket model.
     */
    fun isComposite(): Boolean = subAccounts.isNotEmpty()

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