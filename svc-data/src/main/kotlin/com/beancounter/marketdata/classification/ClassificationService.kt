package com.beancounter.marketdata.classification

import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * An asset's sector and industry classifications, from providers or set by the user.
 * The vocabulary lives in [ClassificationTaxonomy]; fund exposures in [FundLookThrough].
 */
@Service
@Transactional
class ClassificationService(
    private val classificationRepository: AssetClassificationRepository,
    private val exposureRepository: AssetExposureRepository,
    private val taxonomy: ClassificationTaxonomy,
    private val keyGenUtils: KeyGenUtils,
    private val entityManager: EntityManager,
    private val dateUtils: DateUtils = DateUtils()
) {
    /**
     * Classify an asset by a provider's sector and, when present, its industry under that sector.
     * The sector is a raw provider code, normalized to its display name; the industry is a name.
     */
    fun classify(
        asset: Asset,
        standardKey: String,
        sector: String,
        industry: String?,
        source: String
    ) {
        val standard = taxonomy.providerStandard(standardKey)
        val sectorItem =
            taxonomy.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                rawCode = sector
            )
        classifyAsset(asset, standard, sectorItem, ClassificationLevel.SECTOR, source)

        if (!industry.isNullOrBlank()) {
            val industryItem =
                taxonomy.getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.INDUSTRY,
                    name = industry,
                    parent = sectorItem
                )
            classifyAsset(asset, standard, industryItem, ClassificationLevel.INDUSTRY, source)
        }
    }

    /**
     * Classify an asset with a specific classification item.
     * Replaces any existing classification at the same level.
     *
     * A manual (user-defined) classification takes precedence: provider
     * enrichment must not clobber a user override. Provider-over-provider and
     * manual-over-provider replacements still apply.
     */
    fun classifyAsset(
        asset: Asset,
        standard: ClassificationStandard,
        item: ClassificationItem,
        level: ClassificationLevel,
        source: String
    ): AssetClassification {
        val existing = classificationRepository.findByAssetIdAndLevel(asset.id, level).orElse(null)
        if (existing != null && existing.source == SOURCE_MANUAL && source != SOURCE_MANUAL) {
            // User override wins over provider enrichment.
            return existing
        }

        // Remove existing classification at this level. Flush the delete before the insert:
        // Hibernate's action queue orders inserts ahead of deletes at commit, so without it a
        // re-classification of the same (asset, standard, level) hits the unique index.
        if (existing != null) {
            classificationRepository.delete(existing)
            entityManager.flush()
        }

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
                asOf = dateUtils.date
            )
        )
    }

    /**
     * Get all classifications for an asset.
     */
    fun getClassifications(assetId: String): List<AssetClassification> = classificationRepository.findByAssetId(assetId)

    /**
     * Whether the asset already carries classification data of the kind it gets: weighted
     * exposures for a [fund], a direct sector/industry classification otherwise.
     */
    fun isClassified(
        assetId: String,
        fund: Boolean
    ): Boolean =
        if (fund) {
            exposureRepository.findByAssetId(assetId).isNotEmpty()
        } else {
            classificationRepository.findByAssetId(assetId).isNotEmpty()
        }

    companion object {
        const val STANDARD_USER = "USER"
        const val SOURCE_MANUAL = "MANUAL"
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
            taxonomy.getOrCreateStandard(
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
            taxonomy.getOrCreateItem(
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
                asOf = dateUtils.date
            )
        )

        // Optionally save industry classification
        if (!industry.isNullOrBlank()) {
            val industryItem =
                taxonomy.getOrCreateItem(
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
                    asOf = dateUtils.date
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