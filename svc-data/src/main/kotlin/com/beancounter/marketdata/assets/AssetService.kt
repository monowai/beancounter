package com.beancounter.marketdata.assets

import com.beancounter.client.Assets
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Asset CRUD functionality.
 */
@Service
@Import(
    DefaultEnricher::class,
    MarketDataService::class
)
@Transactional
@Suppress("TooManyFunctions") // AssetService has 12 functions, threshold is 11
class AssetService(
    private val enrichmentFactory: EnrichmentFactory,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val keyGenUtils: KeyGenUtils,
    private val assetFinder: AssetFinder,
    private val systemUserService: SystemUserService,
    private val assetCategoryConfig: AssetCategoryConfig,
    private val marketDataRepo: MarketDataRepo,
    private val trnRepository: TrnRepository,
    transactionManager: PlatformTransactionManager
) : Assets {
    // New transaction template for recovery lookups after constraint violations
    private val newTxTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    fun enrich(asset: Asset): Asset {
        val enricher = enrichmentFactory.getEnricher(asset.market)
        if (enricher.canEnrich(asset)) {
            val enriched =
                enricher.enrich(
                    asset.id,
                    asset.market,
                    AssetInput(
                        asset.market.code,
                        asset.code,
                        category = asset.category.uppercase()
                    )
                )
            assetRepository.save(enriched) // Hmm, not sure the Repo should be here
            return enriched
        }
        return asset
    }

    override fun handle(assetRequest: AssetRequest): AssetUpdateResponse {
        val assets =
            assetRequest.data
                .mapValues { (_, assetInput) -> create(assetInput) }
        return AssetUpdateResponse(assets)
    }

    override fun backFillEvents(assetId: String) {
        TODO("Not yet implemented")
    }

    override fun find(assetId: String): Asset = assetFinder.find(assetId)

    fun findOrCreate(assetInput: AssetInput): Asset {
        val localAsset = assetFinder.findLocally(assetInput)
        if (localAsset != null) {
            return localAsset
        }
        val market = marketService.getMarket(assetInput.market)
        val eAsset =
            enrichmentFactory.getEnricher(market).enrich(
                id = keyGenUtils.format(UUID.randomUUID()),
                market = market,
                assetInput = assetInput
            )
        if (marketService.canPersist(market)) {
            return try {
                assetFinder.hydrateAsset(assetRepository.save(eAsset))
            } catch (_: DataIntegrityViolationException) {
                // Race condition: another request created this asset concurrently
                // Use new transaction as current one is marked for rollback
                log.debug("Asset {} already exists, fetching existing", assetInput.code)
                newTxTemplate.execute {
                    assetFinder.findLocally(assetInput)
                } ?: throw BusinessException("Unable to resolve asset ${assetInput.code}")
            }
        }
        throw BusinessException("Unable to resolve asset ${assetInput.code}")
    }

    fun resolveAssets(priceRequest: PriceRequest): PriceRequest {
        val assets = assetRepository.findAllById(priceRequest.assets.map { it.assetId })
        val resolvedAssets =
            priceRequest.assets.map { priceAsset ->
                val asset = assets.find { it.id == priceAsset.assetId }
                if (asset != null) {
                    priceAsset.resolvedAsset = assetFinder.hydrateAsset(asset)
                }
                priceAsset
            }
        return priceRequest.copy(assets = resolvedAssets)
    }

    private fun create(assetInput: AssetInput): Asset {
        val foundAsset = assetFinder.findLocally(assetInput)
        return if (foundAsset == null) {
            // Is the market supported?
            val market = marketService.getMarket(assetInput.market, false)
            // Fill in missing asset attributes
            val asset =
                enrichmentFactory
                    .getEnricher(market)
                    .enrich(
                        id = keyGenUtils.id,
                        market = market,
                        assetInput = assetInput
                    )
            try {
                assetFinder.hydrateAsset(assetRepository.save(asset))
            } catch (_: DataIntegrityViolationException) {
                // Race condition: another request created this asset concurrently
                // Use new transaction as current one is marked for rollback
                log.debug("Asset {} already exists, fetching existing", assetInput.code)
                newTxTemplate.execute {
                    assetFinder.findLocally(assetInput)
                } ?: throw BusinessException("Unable to resolve asset ${assetInput.code}")
            }
        } else {
            foundAsset
        }
    }

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
        // Delete associated transactions first
        trnRepository.deleteByAssetId(assetId)
        // Delete associated market data
        marketDataRepo.deleteByAssetId(assetId)
        assetRepository.delete(asset)
    }

    /**
     * Update an asset owned by the current user.
     * Allows updating code, name, currency (priceSymbol), and category.
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
        assetInput.currency?.let { asset.priceSymbol = it }
        // Update category if provided and different from default
        if (assetInput.category.isNotBlank() && assetInput.category != "Equity") {
            val newCategory = assetCategoryConfig.get(assetInput.category.uppercase())
            if (newCategory != null) {
                asset.assetCategory = newCategory
                asset.category = newCategory.id
            }
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

    /**
     * Update the status of an asset.
     * Use this to deactivate delisted assets so they are excluded from price refresh.
     * @throws NotFoundException if asset not found
     */
    fun updateStatus(
        assetId: String,
        status: Status
    ): Asset {
        val asset =
            assetRepository.findById(assetId).orElseThrow {
                NotFoundException("Asset not found: $assetId")
            }
        // Hydrate first to get the transient market field, then create updated copy
        val hydratedAsset = assetFinder.hydrateAsset(asset)
        val updatedAsset = hydratedAsset.copy(status = status)
        return assetFinder.hydrateAsset(assetRepository.save(updatedAsset))
    }

    companion object {
        private val log = LoggerFactory.getLogger(AssetService::class.java)
    }
}