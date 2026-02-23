package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Service for managing private asset configurations.
 *
 * Handles CRUD operations for asset-level income/expense settings,
 * ensuring users can only access configs for assets they own.
 */
@Service
@Transactional
class PrivateAssetConfigService(
    private val configRepository: PrivateAssetConfigRepository,
    private val assetRepository: AssetRepository,
    private val systemUserService: SystemUserService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Get all configs for assets owned by the current user.
     */
    fun getMyConfigs(): PrivateAssetConfigsResponse {
        val user =
            systemUserService.getActiveUser()
                ?: return PrivateAssetConfigsResponse(emptyList())
        val configs = configRepository.findByUserId(user.id)
        return PrivateAssetConfigsResponse(configs)
    }

    /**
     * Get config for a specific asset.
     * Returns null if no config exists.
     */
    fun getConfig(assetId: String): PrivateAssetConfigResponse? {
        verifyAssetOwnership(assetId)
        val config = configRepository.findById(assetId).orElse(null) ?: return null
        return PrivateAssetConfigResponse(config)
    }

    /**
     * Save (create or update) a config for an asset.
     */
    fun saveConfig(
        assetId: String,
        request: PrivateAssetConfigRequest
    ): PrivateAssetConfigResponse {
        verifyAssetOwnership(assetId)

        log.info(
            "Saving config for asset: $assetId - " +
                "request.isPension=${request.isPension}, " +
                "request.payoutAge=${request.payoutAge}, " +
                "request.lumpSum=${request.lumpSum}, " +
                "request.policyType=${request.policyType}"
        )

        validateSubAccountCodes(request.subAccounts)

        val existing = configRepository.findById(assetId).orElse(null)
        val config =
            existing?.let { ex ->
                val updated =
                    ex.copy(
                        monthlyRentalIncome =
                            if (request.isPrimaryResidence == true) {
                                BigDecimal.ZERO
                            } else {
                                request.monthlyRentalIncome ?: ex.monthlyRentalIncome
                            },
                        rentalCurrency = request.rentalCurrency ?: ex.rentalCurrency,
                        countryCode = request.countryCode?.uppercase() ?: ex.countryCode,
                        monthlyManagementFee = request.monthlyManagementFee ?: ex.monthlyManagementFee,
                        managementFeePercent = request.managementFeePercent ?: ex.managementFeePercent,
                        monthlyBodyCorporateFee = request.monthlyBodyCorporateFee ?: ex.monthlyBodyCorporateFee,
                        annualPropertyTax = request.annualPropertyTax ?: ex.annualPropertyTax,
                        annualInsurance = request.annualInsurance ?: ex.annualInsurance,
                        monthlyOtherExpenses = request.monthlyOtherExpenses ?: ex.monthlyOtherExpenses,
                        deductIncomeTax = request.deductIncomeTax ?: ex.deductIncomeTax,
                        isPrimaryResidence = request.isPrimaryResidence ?: ex.isPrimaryResidence,
                        liquidationPriority = request.liquidationPriority ?: ex.liquidationPriority,
                        transactionDayOfMonth = request.transactionDayOfMonth ?: ex.transactionDayOfMonth,
                        creditAccountId = request.creditAccountId ?: ex.creditAccountId,
                        autoGenerateTransactions = request.autoGenerateTransactions ?: ex.autoGenerateTransactions,
                        expectedReturnRate = request.expectedReturnRate ?: ex.expectedReturnRate,
                        payoutAge = request.payoutAge ?: ex.payoutAge,
                        monthlyPayoutAmount = request.monthlyPayoutAmount ?: ex.monthlyPayoutAmount,
                        lumpSum = request.lumpSum ?: ex.lumpSum,
                        monthlyContribution = request.monthlyContribution ?: ex.monthlyContribution,
                        isPension = request.isPension ?: ex.isPension,
                        policyType = request.policyType ?: ex.policyType,
                        lockedUntilDate = request.lockedUntilDate ?: ex.lockedUntilDate,
                        cpfLifePlan = request.cpfLifePlan ?: ex.cpfLifePlan,
                        cpfPayoutStartAge = request.cpfPayoutStartAge ?: ex.cpfPayoutStartAge,
                        updatedDate = LocalDate.now()
                    )
                mergeSubAccounts(updated, assetId, request.subAccounts)
                updated
            }
                ?: // Create new
                PrivateAssetConfig(
                    assetId = assetId,
                    monthlyRentalIncome =
                        if (request.isPrimaryResidence == true) {
                            BigDecimal.ZERO
                        } else {
                            request.monthlyRentalIncome ?: BigDecimal.ZERO
                        },
                    rentalCurrency = request.rentalCurrency ?: "NZD",
                    countryCode = request.countryCode?.uppercase() ?: "NZ",
                    monthlyManagementFee = request.monthlyManagementFee ?: BigDecimal.ZERO,
                    managementFeePercent = request.managementFeePercent ?: BigDecimal.ZERO,
                    monthlyBodyCorporateFee = request.monthlyBodyCorporateFee ?: BigDecimal.ZERO,
                    annualPropertyTax = request.annualPropertyTax ?: BigDecimal.ZERO,
                    annualInsurance = request.annualInsurance ?: BigDecimal.ZERO,
                    monthlyOtherExpenses = request.monthlyOtherExpenses ?: BigDecimal.ZERO,
                    deductIncomeTax = request.deductIncomeTax ?: false,
                    isPrimaryResidence = request.isPrimaryResidence ?: false,
                    liquidationPriority = request.liquidationPriority ?: 100,
                    transactionDayOfMonth = request.transactionDayOfMonth ?: 1,
                    creditAccountId = request.creditAccountId,
                    autoGenerateTransactions = request.autoGenerateTransactions ?: false,
                    expectedReturnRate = request.expectedReturnRate,
                    payoutAge = request.payoutAge,
                    monthlyPayoutAmount = request.monthlyPayoutAmount,
                    lumpSum = request.lumpSum ?: false,
                    monthlyContribution = request.monthlyContribution,
                    isPension = request.isPension ?: false,
                    policyType = request.policyType,
                    lockedUntilDate = request.lockedUntilDate,
                    cpfLifePlan = request.cpfLifePlan,
                    cpfPayoutStartAge = request.cpfPayoutStartAge,
                    subAccounts = toSubAccountEntities(assetId, request.subAccounts),
                    createdDate = LocalDate.now(),
                    updatedDate = LocalDate.now()
                )

        val saved = configRepository.save(config)
        log.info(
            "Saved config for asset: $assetId, " +
                "rental: ${saved.monthlyRentalIncome} ${saved.rentalCurrency}, " +
                "mgmtFee: ${saved.monthlyManagementFee}, " +
                "isPension: ${saved.isPension}, " +
                "payoutAge: ${saved.payoutAge}, " +
                "lumpSum: ${saved.lumpSum}, " +
                "policyType: ${saved.policyType}, " +
                "subAccounts: ${saved.subAccounts.size}"
        )
        return PrivateAssetConfigResponse(saved)
    }

    /**
     * Validate that sub-account codes are unique within the request.
     */
    private fun validateSubAccountCodes(subAccounts: List<SubAccountRequest>?) {
        if (subAccounts.isNullOrEmpty()) return
        val codes = subAccounts.map { it.code.uppercase() }
        val duplicates = codes.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw BusinessException("Duplicate sub-account codes: ${duplicates.joinToString()}")
        }
    }

    /**
     * Merge sub-accounts from the request into the existing config.
     * Uses orphanRemoval to delete sub-accounts not in the request.
     */
    private fun mergeSubAccounts(
        config: PrivateAssetConfig,
        assetId: String,
        requestSubAccounts: List<SubAccountRequest>?
    ) {
        if (requestSubAccounts == null) return

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

    /**
     * Convert sub-account requests to entities for new configs.
     */
    private fun toSubAccountEntities(
        assetId: String,
        subAccounts: List<SubAccountRequest>?
    ): MutableList<PrivateAssetSubAccount> {
        if (subAccounts.isNullOrEmpty()) return mutableListOf()
        return subAccounts
            .map { req ->
                PrivateAssetSubAccount(
                    assetId = assetId,
                    code = req.code.uppercase(),
                    displayName = req.displayName,
                    balance = req.balance,
                    expectedReturnRate = req.expectedReturnRate,
                    feeRate = req.feeRate,
                    liquid = req.liquid
                )
            }.toMutableList()
    }

    /**
     * Delete config for an asset.
     */
    fun deleteConfig(assetId: String) {
        verifyAssetOwnership(assetId)
        if (!configRepository.existsById(assetId)) {
            throw NotFoundException("Config not found for asset: $assetId")
        }
        configRepository.deleteById(assetId)
        log.info("Deleted config for asset: $assetId")
    }

    /**
     * Get configs for multiple assets (for bulk operations).
     */
    fun getConfigsForAssets(assetIds: Collection<String>): PrivateAssetConfigsResponse {
        val configs = configRepository.findByAssetIdIn(assetIds)
        return PrivateAssetConfigsResponse(configs)
    }

    /**
     * Verify the current user owns the specified asset.
     */
    private fun verifyAssetOwnership(assetId: String) {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        val asset =
            assetRepository.findById(assetId).orElseThrow {
                NotFoundException("Asset not found: $assetId")
            }

        if (asset.systemUser?.id != user.id) {
            throw BusinessException("Asset not owned by current user")
        }
    }
}