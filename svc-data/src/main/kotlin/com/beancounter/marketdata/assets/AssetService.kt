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
class AssetService(
    private val enrichmentFactory: EnrichmentFactory,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val keyGenUtils: KeyGenUtils,
    private val assetFinder: AssetFinder,
    private val assetCategoryConfig: AssetCategoryConfig,
    private val accountingTypeService: AccountingTypeService,
    private val trnRepository: TrnRepository,
    private val assetCascadeDeleter: AssetCascadeDeleter,
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
                // saveAndFlush forces an immediate flush so AssetEntityListener
                // (@PostPersist / @PostUpdate) fires inline and rehydrates the
                // managed instance's `@Transient` fields. Plain save() defers the
                // flush to transaction commit, so callers inside @Transactional
                // boundaries would see a null `market`.
                assetRepository.saveAndFlush(eAsset)
            } catch (_: DataIntegrityViolationException) {
                // Race condition: another request created this asset concurrently
                // Use new transaction as current one is marked for rollback
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
        // Two-stage resolution: prefer bulk lookup by assetId (cheap, single
        // round-trip), then fall back to market+code lookup for entries whose
        // caller only supplied the ticker. The svc-agent `getCurrentPrice`
        // tool, for example, sends `PriceAsset(market, code)` with no id
        // (see DATA-4G: phantom Asset(id=code) propagated into the persistence
        // path and crashed the async save).
        val assetIds =
            priceRequest.assets
                .map { it.assetId }
                .filter { it.isNotBlank() }
        val byId = assetRepository.findAllById(assetIds).associateBy { it.id }

        val resolvedAssets =
            priceRequest.assets.map { priceAsset ->
                if (priceAsset.resolvedAsset != null) return@map priceAsset
                val byIdMatch = byId[priceAsset.assetId]
                val asset =
                    byIdMatch
                        ?: if (priceAsset.market.isNotBlank() && priceAsset.code.isNotBlank()) {
                            try {
                                assetFinder.findLocally(
                                    AssetInput(priceAsset.market, priceAsset.code)
                                )
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                if (asset != null) {
                    priceAsset.resolvedAsset = asset
                }
                priceAsset
            }
        val resolvedCount = resolvedAssets.count { it.resolvedAsset != null }
        if (resolvedCount < priceRequest.assets.size) {
            log.warn(
                "Resolved {} of {} assets from database",
                resolvedCount,
                priceRequest.assets.size
            )
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
                // saveAndFlush — see findOrCreate above for rationale.
                assetRepository.saveAndFlush(asset)
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
     * Admin-only update: rewrite an asset's category and/or display name
     * regardless of ownership. The asset's currency is sourced from the
     * existing accountingType — admin classification edits must not silently
     * re-denominate a public asset. The matching AccountingType row is
     * upserted via AccountingTypeService so existing report-grouping joins
     * stay valid.
     * @throws NotFoundException if asset not found
     */
    fun updateAsset(
        assetId: String,
        input: AssetInput
    ): Asset {
        val asset =
            assetRepository.findById(assetId).orElseThrow {
                NotFoundException("Asset not found: $assetId")
            }
        input.name?.let { asset.name = it }
        if (input.category.isNotBlank()) {
            val newCategoryId = input.category.uppercase()
            val currency =
                asset.accountingType?.currency
                    ?: throw BusinessException(
                        "Cannot resolve currency for asset $assetId — asset has no accountingType"
                    )
            val newAccountingType =
                accountingTypeService.getOrCreate(
                    category = newCategoryId,
                    currency = currency
                )
            asset.accountingType = newAccountingType
            asset.category = newAccountingType.category
            assetCategoryConfig.get(newAccountingType.category)?.let {
                asset.assetCategory = it
            }
        }
        log.info(
            "Admin updated asset {} name='{}' category={}",
            assetId,
            asset.name,
            asset.category
        )
        // saveAndFlush — AssetEntityListener (@PostUpdate) needs an immediate
        // flush to rehydrate transient fields on the managed instance.
        return assetRepository.saveAndFlush(asset)
    }

    /**
     * Admin-only delete of an arbitrary asset (public-market or user-owned).
     *
     * Refuses to delete when the asset is still referenced by any transaction
     * (either as the trade asset or the cash settlement asset). The caller is
     * expected to confirm the asset isn't held in any portfolio before
     * issuing the request — this guard is the server-side enforcement of
     * that invariant.
     *
     * Cascades deletion of remaining dependents (market data, classifications,
     * exposures, holdings, broker settlement accounts, private asset config).
     *
     * @throws NotFoundException if asset not found
     * @throws BusinessException if asset is referenced by transactions
     */
    fun deleteAsset(assetId: String) {
        val asset =
            assetRepository.findById(assetId).orElseThrow {
                NotFoundException("Asset not found: $assetId")
            }
        if (trnRepository.existsByAssetId(assetId)) {
            throw BusinessException(
                "Cannot delete asset $assetId — held in one or more transactions"
            )
        }
        if (trnRepository.existsByCashAssetId(assetId)) {
            throw BusinessException(
                "Cannot delete asset $assetId — referenced as cash settlement on transactions"
            )
        }
        assetCascadeDeleter.deleteDependents(assetId)
        // Force the delete to flush inside this try block so any FK race —
        // a parallel writer adding a referencing row between the
        // existsBy* checks above and the actual delete — surfaces here as
        // DataIntegrityViolationException instead of leaking up at
        // transaction commit time. Translate to BusinessException so the
        // caller gets a 400 with the same "held in transactions" message
        // they'd see from the pre-flight guard.
        try {
            assetRepository.delete(asset)
            assetRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            log.warn(
                "FK race deleting asset {} ({}.{}): {}",
                assetId,
                asset.market.code,
                asset.code,
                e.mostSpecificCause.message
            )
            throw BusinessException(
                "Cannot delete asset $assetId — held in one or more transactions"
            )
        }
        log.info("Admin deleted asset {} ({}.{})", assetId, asset.market.code, asset.code)
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
        // saveAndFlush so AssetEntityListener (@PostUpdate) fires inline and
        // hydrates the managed instance before we hand it back to the caller.
        return assetRepository.saveAndFlush(asset.copy(status = status))
    }

    companion object {
        private val log = LoggerFactory.getLogger(AssetService::class.java)
    }
}