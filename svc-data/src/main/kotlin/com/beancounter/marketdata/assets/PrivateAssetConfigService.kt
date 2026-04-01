package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
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
    private val systemUserService: SystemUserService,
    private val trnRepository: TrnRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Get all configs for assets owned by the current user.
     *
     * @param excludePortfolioIds If provided, configs for assets that have transactions
     *   in any of the specified portfolios will be excluded from the results.
     */
    fun getMyConfigs(excludePortfolioIds: Set<String>? = null): PrivateAssetConfigsResponse {
        val user =
            systemUserService.getActiveUser()
                ?: return PrivateAssetConfigsResponse(emptyList())
        var configs = configRepository.findByUserId(user.id)

        if (!excludePortfolioIds.isNullOrEmpty()) {
            val excludedAssetIds =
                trnRepository.findDistinctAssetIdsByPortfolioIds(excludePortfolioIds).toSet()
            configs = configs.filter { it.assetId !in excludedAssetIds }
        }

        // Resolve asset names
        val assetNames =
            configs.associate { config ->
                val asset = assetRepository.findById(config.assetId).orElse(null)
                config.assetId to (asset?.name ?: config.assetId)
            }

        return PrivateAssetConfigsResponse(configs, assetNames)
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
        logSaveRequest(assetId, request)
        validateSubAccountCodes(request.subAccounts)

        val existing = configRepository.findById(assetId).orElse(null)
        val config =
            if (existing != null) {
                updateExistingConfig(existing, assetId, request)
            } else {
                createNewConfig(assetId, request)
            }

        val saved = configRepository.save(config)
        logSavedConfig(assetId, saved)
        return PrivateAssetConfigResponse(saved)
    }

    private fun logSaveRequest(
        assetId: String,
        request: PrivateAssetConfigRequest
    ) {
        log.info(
            "Saving config for asset: $assetId - " +
                "request.isPension=${request.isPension}, " +
                "request.payoutAge=${request.payoutAge}, " +
                "request.lumpSum=${request.lumpSum}, " +
                "request.policyType=${request.policyType}"
        )
    }

    private fun logSavedConfig(
        assetId: String,
        saved: PrivateAssetConfig
    ) {
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
    }

    /**
     * Resolve the monthly rental income from the request,
     * zeroing it out when the asset is marked as a primary residence.
     */
    private fun resolveRentalIncome(
        request: PrivateAssetConfigRequest,
        fallback: BigDecimal
    ): BigDecimal =
        if (request.isPrimaryResidence == true) {
            BigDecimal.ZERO
        } else {
            request.monthlyRentalIncome ?: fallback
        }

    /**
     * Update an existing config by merging request values with current values.
     */
    private fun updateExistingConfig(
        existing: PrivateAssetConfig,
        assetId: String,
        request: PrivateAssetConfigRequest
    ): PrivateAssetConfig {
        val updated =
            existing
                .copy(
                    monthlyRentalIncome = resolveRentalIncome(request, existing.monthlyRentalIncome),
                    rentalCurrency = request.rentalCurrency ?: existing.rentalCurrency,
                    countryCode = request.countryCode?.uppercase() ?: existing.countryCode,
                    updatedDate = LocalDate.now()
                ).mergeExpenses(request)
                .mergePropertyFlags(request)
                .mergeTransactionSettings(request)
                .mergePensionSettings(request)
                .mergeCpfSettings(request)
        mergeSubAccounts(updated, assetId, request.subAccounts)
        return updated
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
     * Create a new config with request values and sensible defaults.
     */
    private fun createNewConfig(
        assetId: String,
        request: PrivateAssetConfigRequest
    ): PrivateAssetConfig =
        PrivateAssetConfig(
            assetId = assetId,
            monthlyRentalIncome = resolveRentalIncome(request, BigDecimal.ZERO),
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