package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetExposure
import com.beancounter.common.model.AssetHolding
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Fund look-through: the weighted sector exposures and top holdings inside an ETF or fund.
 */
@Service
@Transactional
class FundLookThrough(
    private val exposureRepository: AssetExposureRepository,
    private val holdingRepository: AssetHoldingRepository,
    private val keyGenUtils: KeyGenUtils,
    private val entityManager: EntityManager,
    private val dateUtils: DateUtils = DateUtils()
) {
    /**
     * Add or update exposure for an asset to a classification item.
     */
    fun addExposure(
        asset: Asset,
        standard: ClassificationStandard,
        item: ClassificationItem,
        weight: BigDecimal,
        asOf: LocalDate = dateUtils.date
    ): AssetExposure {
        // Use a managed reference to ensure proper FK handling
        val managedAsset = entityManager.getReference(Asset::class.java, asset.id)

        return exposureRepository.save(
            AssetExposure(
                id = keyGenUtils.id,
                asset = managedAsset,
                standard = standard,
                item = item,
                weight = weight,
                asOf = asOf
            )
        )
    }

    /**
     * Clear all exposures for an asset (used before re-enrichment).
     */
    fun clearExposures(assetId: String) {
        exposureRepository.deleteByAssetId(assetId)
    }

    /**
     * Add a holding to an ETF/fund.
     */
    fun addHolding(
        asset: Asset,
        symbol: String,
        name: String?,
        weight: BigDecimal,
        asOf: LocalDate = dateUtils.date
    ): AssetHolding {
        val managedAsset = entityManager.getReference(Asset::class.java, asset.id)

        return holdingRepository.save(
            AssetHolding(
                id = keyGenUtils.id,
                parentAsset = managedAsset,
                symbol = symbol,
                name = name,
                weight = weight,
                asOf = asOf
            )
        )
    }

    /**
     * Clear all holdings for an asset (used before re-enrichment).
     */
    fun clearHoldings(assetId: String) {
        holdingRepository.deleteByAssetId(assetId)
    }

    /**
     * Get all holdings for an asset (top holdings within an ETF).
     */
    fun getHoldings(assetId: String): List<AssetHolding> = holdingRepository.findByAssetId(assetId)

    /**
     * Get all exposures for an asset.
     */
    fun getExposures(assetId: String): List<AssetExposure> = exposureRepository.findByAssetId(assetId)
}