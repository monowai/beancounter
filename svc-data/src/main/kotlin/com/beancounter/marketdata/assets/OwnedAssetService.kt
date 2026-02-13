package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.marketdata.broker.BrokerSettlementAccountRepository
import com.beancounter.marketdata.classification.AssetClassificationRepository
import com.beancounter.marketdata.classification.AssetExposureRepository
import com.beancounter.marketdata.classification.AssetHoldingRepository
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * User-scoped asset management operations.
 * Handles CRUD for assets owned by the authenticated user.
 */
@Service
@Transactional
class OwnedAssetService(
    private val assetRepository: AssetRepository,
    private val assetFinder: AssetFinder,
    private val systemUserService: SystemUserService,
    private val assetCategoryConfig: AssetCategoryConfig,
    private val accountingTypeService: AccountingTypeService,
    private val currencyService: CurrencyService,
    private val marketDataRepo: MarketDataRepo,
    private val trnRepository: TrnRepository,
    private val assetClassificationRepository: AssetClassificationRepository,
    private val assetExposureRepository: AssetExposureRepository,
    private val assetHoldingRepository: AssetHoldingRepository,
    private val privateAssetConfigRepository: PrivateAssetConfigRepository,
    private val brokerSettlementAccountRepository: BrokerSettlementAccountRepository
) {
    /**
     * Find all assets owned by the current user with a specific category.
     */
    fun findByOwnerAndCategory(category: String): AssetUpdateResponse {
        val user =
            systemUserService.getActiveUser()
                ?: return AssetUpdateResponse(emptyMap())
        val assets = assetRepository.findBySystemUserIdAndCategory(user.id, category.uppercase())
        return AssetUpdateResponse(
            assets.associate { getDisplayCode(it.code) to assetFinder.hydrateAsset(it) }
        )
    }

    /**
     * Find all assets owned by the current user (all categories).
     */
    fun findByOwner(): AssetUpdateResponse {
        val user =
            systemUserService.getActiveUser()
                ?: return AssetUpdateResponse(emptyMap())
        val assets = assetRepository.findBySystemUserId(user.id)
        return AssetUpdateResponse(
            assets.associate { getDisplayCode(it.code) to assetFinder.hydrateAsset(it) }
        )
    }

    /**
     * Extract the display code from a full asset code, stripping the owner prefix.
     * e.g., "userId.WISE" -> "WISE"
     */
    private fun getDisplayCode(code: String): String {
        val dotIndex = code.lastIndexOf(".")
        return if (dotIndex >= 0) code.substring(dotIndex + 1) else code
    }

    /**
     * Delete an asset owned by the current user.
     * Cascades deletion to associated transactions and market data.
     * @throws NotFoundException if asset not found
     * @throws BusinessException if asset not owned by current user
     */
    fun deleteOwnedAsset(assetId: String) {
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
        // Delete in FK dependency order
        trnRepository.clearCashAssetReferences(assetId)
        trnRepository.deleteByAssetId(assetId)
        marketDataRepo.deleteByAssetId(assetId)
        assetClassificationRepository.deleteByAssetId(assetId)
        assetExposureRepository.deleteByAssetId(assetId)
        assetHoldingRepository.deleteByAssetId(assetId)
        brokerSettlementAccountRepository.deleteByAccountId(assetId)
        privateAssetConfigRepository.deleteById(assetId)
        assetRepository.delete(asset)
    }

    /**
     * Update an asset owned by the current user.
     * Allows updating code, name, currency (via accountingType), and category.
     * @throws NotFoundException if asset not found
     * @throws BusinessException if asset not owned by current user
     */
    fun updateOwnedAsset(
        assetId: String,
        assetInput: AssetInput
    ): Asset {
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
        // Update allowed fields
        if (assetInput.code.isNotBlank()) {
            // Preserve user prefix when updating code
            val newCode = "${user.id}.${assetInput.code.uppercase()}"
            asset.code = newCode
        }
        assetInput.name?.let { asset.name = it }
        // Update category and/or currency via accountingType
        val newCategoryId = assetInput.category.ifBlank { asset.category }
        val resolvedCurrency =
            if (assetInput.currency != null) {
                currencyService.getCode(assetInput.currency!!)
            } else {
                asset.accountingType?.currency ?: currencyService.getCode(asset.market.currency.code)
            }
        val newAccountingType =
            accountingTypeService.getOrCreate(
                category = newCategoryId,
                currency = resolvedCurrency
            )
        asset.accountingType = newAccountingType
        asset.category = newAccountingType.category
        val newCategory = assetCategoryConfig.get(newAccountingType.category)
        if (newCategory != null) {
            asset.assetCategory = newCategory
        }
        // Update expected return rate for retirement projections
        assetInput.expectedReturnRate?.let { asset.expectedReturnRate = it }
        return assetFinder.hydrateAsset(assetRepository.save(asset))
    }

    /**
     * Get the current user's ID for asset ownership.
     * @throws BusinessException if user not authenticated
     */
    fun getCurrentOwnerId(): String =
        systemUserService.getActiveUser()?.id
            ?: throw BusinessException("User not authenticated")
}