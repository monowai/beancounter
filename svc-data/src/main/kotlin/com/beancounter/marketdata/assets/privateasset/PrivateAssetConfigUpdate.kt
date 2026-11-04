package com.beancounter.marketdata.assets.privateasset

import com.beancounter.common.exception.BusinessException
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One save of a [PrivateAssetConfig]: the request checked, then merged over the stored config.
 */
class PrivateAssetConfigUpdate(
    private val request: PrivateAssetConfigRequest
) {
    /**
     * Reject a request that cannot be saved: duplicate sub-account codes, or an unknown
     * tax treatment.
     */
    fun validate() {
        validateSubAccountCodes()
        validateTaxTreatment()
    }

    /**
     * The request's values over [existing]'s, dated [today]. A field the request omits keeps
     * its current value; sub-accounts are replaced when the request carries them.
     */
    fun applyTo(
        existing: PrivateAssetConfig,
        assetId: String,
        today: LocalDate
    ): PrivateAssetConfig {
        val updated =
            existing
                .copy(
                    monthlyRentalIncome = rentalIncome(existing.monthlyRentalIncome),
                    rentalCurrency = request.rentalCurrency ?: existing.rentalCurrency,
                    countryCode = request.countryCode?.uppercase() ?: existing.countryCode,
                    updatedDate = today
                ).mergeExpenses(request)
                .mergePropertyFlags(request)
                .mergeTransactionSettings(request)
                .mergePensionSettings(request)
                .mergeCpfSettings(request)
                .mergeUsUkPolicySettings(request)
        mergeSubAccounts(updated, assetId)
        return updated
    }

    /**
     * Resolve the monthly rental income from the request,
     * zeroing it out when the asset is marked as a primary residence.
     */
    private fun rentalIncome(fallback: BigDecimal): BigDecimal =
        if (request.isPrimaryResidence == true) {
            BigDecimal.ZERO
        } else {
            request.monthlyRentalIncome ?: fallback
        }

    /**
     * Validate that sub-account codes are unique within the request.
     */
    private fun validateSubAccountCodes() {
        val subAccounts = request.subAccounts
        if (subAccounts.isNullOrEmpty()) return
        val codes = subAccounts.map { it.code.uppercase() }
        val duplicates = codes.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw BusinessException("Duplicate sub-account codes: ${duplicates.joinToString()}")
        }
    }

    /**
     * Reject an unknown [PrivateAssetConfigRequest.taxTreatment] string.
     * Permissive on which [PolicyType] it's attached to (MVP) — only the
     * value itself is validated against [TaxTreatment].
     */
    private fun validateTaxTreatment() {
        val value = request.taxTreatment ?: return
        if (TaxTreatment.entries.none { it.name == value }) {
            throw BusinessException(
                "Invalid taxTreatment: $value. Must be one of: " +
                    TaxTreatment.entries.joinToString { it.name }
            )
        }
    }

    /**
     * Merge sub-accounts from the request into the existing config.
     * Uses orphanRemoval to delete sub-accounts not in the request.
     */
    private fun mergeSubAccounts(
        config: PrivateAssetConfig,
        assetId: String
    ) {
        val requestSubAccounts = request.subAccounts ?: return

        val existingByCode = config.subAccounts.associateBy { it.code.uppercase() }
        val updatedSubAccounts =
            requestSubAccounts.map { req ->
                val code = req.code.uppercase()
                val existing = existingByCode[code]
                if (existing != null) {
                    existing.copy(
                        displayName = req.displayName,
                        balance = req.balance,
                        expectedReturnRate = req.expectedReturnRate,
                        feeRate = req.feeRate,
                        liquid = req.liquid
                    )
                } else {
                    PrivateAssetSubAccount(
                        assetId = assetId,
                        code = code,
                        displayName = req.displayName,
                        balance = req.balance,
                        expectedReturnRate = req.expectedReturnRate,
                        feeRate = req.feeRate,
                        liquid = req.liquid
                    )
                }
            }
        config.subAccounts.clear()
        config.subAccounts.addAll(updatedSubAccounts)
    }
}

private fun PrivateAssetConfig.mergeExpenses(request: PrivateAssetConfigRequest) =
    copy(
        monthlyManagementFee = request.monthlyManagementFee ?: monthlyManagementFee,
        managementFeePercent = request.managementFeePercent ?: managementFeePercent,
        monthlyBodyCorporateFee = request.monthlyBodyCorporateFee ?: monthlyBodyCorporateFee,
        annualPropertyTax = request.annualPropertyTax ?: annualPropertyTax,
        annualInsurance = request.annualInsurance ?: annualInsurance,
        monthlyOtherExpenses = request.monthlyOtherExpenses ?: monthlyOtherExpenses
    )

private fun PrivateAssetConfig.mergePropertyFlags(request: PrivateAssetConfigRequest) =
    copy(
        deductIncomeTax = request.deductIncomeTax ?: deductIncomeTax,
        isPrimaryResidence = request.isPrimaryResidence ?: isPrimaryResidence,
        liquidationPriority = request.liquidationPriority ?: liquidationPriority
    )

private fun PrivateAssetConfig.mergeTransactionSettings(request: PrivateAssetConfigRequest) =
    copy(
        transactionDayOfMonth = request.transactionDayOfMonth ?: transactionDayOfMonth,
        creditAccountId = request.creditAccountId ?: creditAccountId,
        autoGenerateTransactions = request.autoGenerateTransactions ?: autoGenerateTransactions,
        expectedReturnRate = request.expectedReturnRate ?: expectedReturnRate
    )

private fun PrivateAssetConfig.mergePensionSettings(request: PrivateAssetConfigRequest) =
    copy(
        payoutAge = request.payoutAge ?: payoutAge,
        monthlyPayoutAmount = request.monthlyPayoutAmount ?: monthlyPayoutAmount,
        lumpSum = request.lumpSum ?: lumpSum,
        monthlyContribution = request.monthlyContribution ?: monthlyContribution,
        contributionFrequency = request.contributionFrequency ?: contributionFrequency,
        isPension = request.isPension ?: isPension,
        policyType = request.policyType ?: policyType,
        lockedUntilDate = request.lockedUntilDate ?: lockedUntilDate
    )

private fun PrivateAssetConfig.mergeCpfSettings(request: PrivateAssetConfigRequest) =
    copy(
        cpfLifePlan = request.cpfLifePlan ?: cpfLifePlan,
        cpfPayoutStartAge = request.cpfPayoutStartAge ?: cpfPayoutStartAge
    )

/**
 * Merge US 401k/IRA + UK ISA settings. Kept permissive (MVP): fields are
 * accepted regardless of [PrivateAssetConfig.policyType] and are harmless
 * (unused) on policy types that don't consume them.
 */
private fun PrivateAssetConfig.mergeUsUkPolicySettings(request: PrivateAssetConfigRequest) =
    copy(
        taxTreatment = request.taxTreatment?.let { TaxTreatment.valueOf(it) } ?: taxTreatment,
        employeeDeferralPercent = request.employeeDeferralPercent ?: employeeDeferralPercent,
        employerMatchPercent = request.employerMatchPercent ?: employerMatchPercent,
        employerMatchCapPercent = request.employerMatchCapPercent ?: employerMatchCapPercent,
        withdrawalTaxRate = request.withdrawalTaxRate ?: withdrawalTaxRate
    )