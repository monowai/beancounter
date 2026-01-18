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

        val existing = configRepository.findById(assetId).orElse(null)
        val config =
            existing?.copy(
                monthlyRentalIncome =
                    if (request.isPrimaryResidence == true) {
                        BigDecimal.ZERO
                    } else {
                        request.monthlyRentalIncome ?: existing.monthlyRentalIncome
                    },
                rentalCurrency = request.rentalCurrency ?: existing.rentalCurrency,
                countryCode = request.countryCode?.uppercase() ?: existing.countryCode,
                monthlyManagementFee = request.monthlyManagementFee ?: existing.monthlyManagementFee,
                managementFeePercent = request.managementFeePercent ?: existing.managementFeePercent,
                monthlyBodyCorporateFee = request.monthlyBodyCorporateFee ?: existing.monthlyBodyCorporateFee,
                annualPropertyTax = request.annualPropertyTax ?: existing.annualPropertyTax,
                annualInsurance = request.annualInsurance ?: existing.annualInsurance,
                monthlyOtherExpenses = request.monthlyOtherExpenses ?: existing.monthlyOtherExpenses,
                deductIncomeTax = request.deductIncomeTax ?: existing.deductIncomeTax,
                isPrimaryResidence = request.isPrimaryResidence ?: existing.isPrimaryResidence,
                liquidationPriority = request.liquidationPriority ?: existing.liquidationPriority,
                transactionDayOfMonth = request.transactionDayOfMonth ?: existing.transactionDayOfMonth,
                creditAccountId = request.creditAccountId ?: existing.creditAccountId,
                autoGenerateTransactions = request.autoGenerateTransactions ?: existing.autoGenerateTransactions,
                expectedReturnRate = request.expectedReturnRate ?: existing.expectedReturnRate,
                payoutAge = request.payoutAge ?: existing.payoutAge,
                monthlyPayoutAmount = request.monthlyPayoutAmount ?: existing.monthlyPayoutAmount,
                isPension = request.isPension ?: existing.isPension,
                updatedDate = LocalDate.now()
            )
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
                    isPension = request.isPension ?: false,
                    createdDate = LocalDate.now(),
                    updatedDate = LocalDate.now()
                )

        val saved = configRepository.save(config)
        log.info(
            "Saved config for asset: $assetId, " +
                "rental: ${saved.monthlyRentalIncome} ${saved.rentalCurrency}, " +
                "mgmtFee: ${saved.monthlyManagementFee}"
        )
        return PrivateAssetConfigResponse(saved)
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