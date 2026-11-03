package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * Admin changes to any asset, whoever owns it: rename or reclassify, change status, delete.
 */
@Service
@Transactional
class AssetAdministration(
    private val assetRepository: AssetRepository,
    private val assetCategoryConfig: AssetCategoryConfig,
    private val accountingTypeService: AccountingTypeService,
    private val trnRepository: TrnRepository,
    private val assetCascadeDeleter: AssetCascadeDeleter
) {
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
        val asset = existing(assetId)
        input.name?.let { asset.name = it }
        input.priceSymbol?.let { asset.priceSymbol = it }
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
        val asset = existing(assetId)
        requireUnreferenced(assetId)
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
            throw BusinessException(heldInTransactions(assetId))
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
        val asset = existing(assetId)
        // saveAndFlush so AssetEntityListener (@PostUpdate) fires inline and
        // hydrates the managed instance before we hand it back to the caller.
        return assetRepository.saveAndFlush(asset.copy(status = status))
    }

    private fun existing(assetId: String): Asset =
        assetRepository.findById(assetId).orElseThrow {
            NotFoundException("Asset not found: $assetId")
        }

    private fun requireUnreferenced(assetId: String) {
        if (trnRepository.existsByAssetId(assetId)) {
            throw BusinessException(heldInTransactions(assetId))
        }
        if (trnRepository.existsByCashAssetId(assetId)) {
            throw BusinessException(
                "Cannot delete asset $assetId — referenced as cash settlement on transactions"
            )
        }
    }

    private fun heldInTransactions(assetId: String) = "Cannot delete asset $assetId — held in one or more transactions"

    private companion object {
        val log = LoggerFactory.getLogger(AssetAdministration::class.java)
    }
}