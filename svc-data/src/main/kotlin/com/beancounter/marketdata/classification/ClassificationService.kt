package com.beancounter.marketdata.classification

import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.AssetExposure
import com.beancounter.common.model.AssetHolding
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Service for managing asset classifications and exposures.
 */
@Service
@Transactional
class ClassificationService(
    private val standardRepository: ClassificationStandardRepository,
    private val itemRepository: ClassificationItemRepository,
    private val classificationRepository: AssetClassificationRepository,
    private val exposureRepository: AssetExposureRepository,
    private val holdingRepository: AssetHoldingRepository,
    private val sectorNormalizer: SectorNormalizer,
    private val keyGenUtils: KeyGenUtils,
    private val entityManager: EntityManager
) {
    /**
     * Get or create a classification standard.
     */
    fun getOrCreateStandard(
        key: String,
        name: String,
        version: String = "1.0",
        provider: String = ClassificationStandard.PROVIDER_ALPHA
    ): ClassificationStandard =
        standardRepository.findByKey(key).orElseGet {
            standardRepository.save(
                ClassificationStandard(
                    id = keyGenUtils.id,
                    key = key,
                    name = name,
                    version = version,
                    provider = provider
                )
            )
        }

    /**
     * Get or create a classification item.
     * User provides the display name; system calculates the unique UPPER_CASE_CODE.
     *
     * @param name The display name (e.g., "Multi Sector", "Information Technology")
     * @param rawCode Optional code from external source (e.g., "TECHNOLOGY" from AlphaVantage)
     *                - used only to derive name via SectorNormalizer when name is not provided
     */
    fun getOrCreateItem(
        standard: ClassificationStandard,
        level: ClassificationLevel,
        name: String? = null,
        rawCode: String? = null,
        parent: ClassificationItem? = null
    ): ClassificationItem {
        // Determine the display name
        val displayName =
            when {
                !name.isNullOrBlank() -> {
                    name
                }
                !rawCode.isNullOrBlank() && level == ClassificationLevel.SECTOR -> {
                    sectorNormalizer.normalize(rawCode)
                }
                !rawCode.isNullOrBlank() -> {
                    rawCode
                }
                else -> {
                    throw IllegalArgumentException("Either name or rawCode must be provided")
                }
            }
        // Calculate unique code from the display name
        val normalizedCode = displayName.uppercase().replace(" ", "_")

        val existing =
            itemRepository.findByStandardIdAndLevelAndCode(
                standard.id,
                level,
                normalizedCode
            )

        return if (existing.isPresent) {
            val item = existing.get()
            // Update name if it differs
            if (item.name != displayName) {
                item.name = displayName
                itemRepository.save(item)
            } else {
                item
            }
        } else {
            itemRepository.save(
                ClassificationItem(
                    id = keyGenUtils.id,
                    standard = standard,
                    level = level,
                    code = normalizedCode,
                    name = displayName,
                    parent = parent
                )
            )
        }
    }

    /**
     * Classify an asset with a specific classification item.
     * Replaces any existing classification at the same level.
     */
    fun classifyAsset(
        asset: Asset,
        standard: ClassificationStandard,
        item: ClassificationItem,
        level: ClassificationLevel,
        source: String
    ): AssetClassification {
        // Remove existing classification at this level
        classificationRepository
            .findByAssetIdAndLevel(asset.id, level)
            .ifPresent { classificationRepository.delete(it) }

        // Use a managed reference to ensure proper FK handling
        val managedAsset = entityManager.getReference(Asset::class.java, asset.id)

        return classificationRepository.save(
            AssetClassification(
                id = keyGenUtils.id,
                asset = managedAsset,
                standard = standard,
                item = item,
                level = level,
                source = source,
                asOf = LocalDate.now()
            )
        )
    }

    /**
     * Add or update exposure for an asset to a classification item.
     */
    fun addExposure(
        asset: Asset,
        standard: ClassificationStandard,
        item: ClassificationItem,
        weight: BigDecimal,
        asOf: LocalDate = LocalDate.now()
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
        asOf: LocalDate = LocalDate.now()
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
     * Get all classifications for an asset.
     */
    fun getClassifications(assetId: String): List<AssetClassification> = classificationRepository.findByAssetId(assetId)

    /**
     * Get all exposures for an asset.
     */
    fun getExposures(assetId: String): List<AssetExposure> = exposureRepository.findByAssetId(assetId)

    /**
     * Check if an asset has any classifications.
     */
    fun hasClassifications(assetId: String): Boolean = classificationRepository.findByAssetId(assetId).isNotEmpty()

    /**
     * Check if an asset has any exposures.
     */
    fun hasExposures(assetId: String): Boolean = exposureRepository.findByAssetId(assetId).isNotEmpty()

    /**
     * Get the default AlphaVantage classification standard.
     */
    fun getAlphaStandard(): ClassificationStandard =
        getOrCreateStandard(
            key = ClassificationStandard.ALPHA,
            name = "AlphaVantage Sector Classification",
            version = "1.0",
            provider = ClassificationStandard.PROVIDER_ALPHA
        )

    /**
     * Get all distinct sectors in the system.
     */
    fun getAllSectors(): List<ClassificationItem> =
        itemRepository
            .findByLevel(ClassificationLevel.SECTOR)
            .sortedBy { it.name }

    /**
     * Count total classifications (for debugging).
     */
    fun countClassifications(): Long = classificationRepository.count()

    /**
     * Count total exposures (for debugging).
     */
    fun countExposures(): Long = exposureRepository.count()

    /**
     * Get sample classifications for debugging.
     */
    fun getSampleClassifications(): List<Map<String, Any?>> =
        classificationRepository.findAll().take(5).map { ac ->
            mapOf(
                "id" to ac.id,
                "assetId" to ac.asset.id,
                "assetCode" to ac.asset.code,
                "level" to ac.level.name,
                "itemName" to ac.item.name
            )
        }

    /**
     * Get sample exposures for debugging.
     */
    fun getSampleExposures(): List<Map<String, Any?>> =
        exposureRepository.findAll().take(5).map { ae ->
            mapOf(
                "id" to ae.id,
                "assetId" to ae.asset.id,
                "assetCode" to ae.asset.code,
                "sectorName" to ae.item.name,
                "weight" to ae.weight
            )
        }

    companion object {
        const val STANDARD_USER = "USER"
        const val SOURCE_MANUAL = "MANUAL"
    }

    /**
     * Delete a sector (classification item) and remove all asset classifications using it.
     * Only USER-defined sectors can be deleted.
     * @return the number of affected assets
     */
    fun deleteSector(sectorCode: String): Int {
        val userStandard = standardRepository.findByKey(STANDARD_USER)
        if (userStandard.isEmpty) {
            return 0
        }

        val item =
            itemRepository.findByStandardIdAndLevelAndCode(
                userStandard.get().id,
                ClassificationLevel.SECTOR,
                sectorCode
            )

        if (item.isEmpty) {
            return 0
        }

        val sectorItem = item.get()
        val affectedClassifications = classificationRepository.countByItemId(sectorItem.id)
        val affectedExposures = exposureRepository.countByItemId(sectorItem.id)

        // Delete all classifications and exposures using this sector
        classificationRepository.deleteByItemId(sectorItem.id)
        exposureRepository.deleteByItemId(sectorItem.id)

        // Delete the sector item itself
        itemRepository.delete(sectorItem)

        return affectedClassifications + affectedExposures
    }

    /**
     * Manually set a sector classification for an asset.
     * Used for user-defined categories like "Income", "Diversified", "Index".
     * This clears any existing classifications and exposures for the asset.
     */
    fun setManualClassification(
        assetId: String,
        sector: String,
        industry: String? = null
    ): AssetClassificationSummary {
        // Get or create the USER standard for manual classifications
        val standard =
            getOrCreateStandard(
                key = STANDARD_USER,
                name = "User-Defined Classification",
                version = "1.0",
                provider = "USER"
            )

        // Clear existing classifications and exposures
        classificationRepository.deleteByAssetId(assetId)
        exposureRepository.deleteByAssetId(assetId)

        // Create sector item - user provides name, system calculates code
        val sectorItem =
            getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                name = sector
            )

        // Create a proxy asset reference
        val assetRef = entityManager.getReference(Asset::class.java, assetId)

        // Save sector classification
        classificationRepository.save(
            AssetClassification(
                id = keyGenUtils.id,
                asset = assetRef,
                standard = standard,
                item = sectorItem,
                level = ClassificationLevel.SECTOR,
                source = SOURCE_MANUAL,
                asOf = LocalDate.now()
            )
        )

        // Optionally save industry classification
        if (!industry.isNullOrBlank()) {
            val industryItem =
                getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.INDUSTRY,
                    name = industry,
                    parent = sectorItem
                )
            classificationRepository.save(
                AssetClassification(
                    id = keyGenUtils.id,
                    asset = assetRef,
                    standard = standard,
                    item = industryItem,
                    level = ClassificationLevel.INDUSTRY,
                    source = SOURCE_MANUAL,
                    asOf = LocalDate.now()
                )
            )
        }

        return AssetClassificationSummary(
            assetId = assetId,
            sector = sector,
            industry = industry
        )
    }

    /**
     * Get classification summaries for multiple assets in bulk.
     * Returns a map of assetId -> classification summary.
     * For equities: returns sector/industry from AssetClassification.
     * For ETFs: returns primary sector (highest weight) from AssetExposure.
     */
    fun getClassificationSummaries(assetIds: List<String>): Map<String, AssetClassificationSummary> {
        if (assetIds.isEmpty()) return emptyMap()

        val classifications = classificationRepository.findByAssetIdIn(assetIds)
        val exposures = exposureRepository.findByAssetIdIn(assetIds)

        return assetIds.associateWith { assetId ->
            val assetClassifications = classifications.filter { it.assetId == assetId }

            // First try direct classifications (equities)
            var sector =
                assetClassifications
                    .find { it.level == ClassificationLevel.SECTOR }
                    ?.item
                    ?.name
            val industry =
                assetClassifications
                    .find { it.level == ClassificationLevel.INDUSTRY }
                    ?.item
                    ?.name

            // If no sector from classification, try exposures (ETFs) - use primary sector
            if (sector == null) {
                sector =
                    exposures
                        .filter { it.assetId == assetId }
                        .maxByOrNull { it.weight }
                        ?.item
                        ?.name
            }

            AssetClassificationSummary(
                assetId = assetId,
                sector = sector,
                industry = industry
            )
        }
    }
}