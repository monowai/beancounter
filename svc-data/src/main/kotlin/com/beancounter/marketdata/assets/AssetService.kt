package com.beancounter.marketdata.assets

import com.beancounter.client.Assets
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
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
class AssetService(
    private val enrichmentFactory: EnrichmentFactory,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val keyGenUtils: KeyGenUtils,
    private val assetFinder: AssetFinder,
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
                        category = asset.category.uppercase(),
                        currency = asset.accountingType?.currency?.code ?: asset.market.currency.code
                    )
                )
            assetRepository.save(enriched)
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

    fun resolveAsset(priceAsset: PriceAsset): Asset? =
        try {
            if (priceAsset.assetId.isNotBlank()) {
                assetFinder.find(priceAsset.assetId)
            } else {
                assetFinder.findLocally(AssetInput(priceAsset.market, priceAsset.code))
            }
        } catch (_: Exception) {
            log.warn(
                "Could not resolve asset: market={}, code={}, id={}",
                priceAsset.market,
                priceAsset.code,
                priceAsset.assetId
            )
            null
        }

    fun resolveAssets(priceRequest: PriceRequest): PriceRequest {
        val assetIds = priceRequest.assets.map { it.assetId }
        val assets = assetRepository.findAllById(assetIds)
        val resolvedCount =
            priceRequest.assets.count { priceAsset ->
                assets.find { it.id == priceAsset.assetId } != null
            }
        if (resolvedCount < assetIds.size) {
            log.warn(
                "Resolved {} of {} assets from database",
                resolvedCount,
                assetIds.size
            )
        }
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