package com.beancounter.marketdata.classification

import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.KeyGenUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The classification vocabulary: standards, their sector and industry items, and the
 * user-defined sectors that can be deleted.
 */
@Service
@Transactional
class ClassificationTaxonomy(
    private val standardRepository: ClassificationStandardRepository,
    private val itemRepository: ClassificationItemRepository,
    private val classificationRepository: AssetClassificationRepository,
    private val exposureRepository: AssetExposureRepository,
    private val sectorNormalizer: SectorNormalizer,
    private val keyGenUtils: KeyGenUtils
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
     * The classification standard of a known provider ([ClassificationStandard.ALPHA],
     * [ClassificationStandard.EODHD] or [ClassificationStandard.SEC]), created on first use.
     */
    fun providerStandard(key: String): ClassificationStandard {
        val (name, provider) = PROVIDER_STANDARDS.getValue(key)
        return getOrCreateStandard(key = key, name = name, version = "1.0", provider = provider)
    }

    /**
     * Get all distinct sectors in the system.
     */
    fun getAllSectors(): List<ClassificationItem> =
        itemRepository
            .findByLevel(ClassificationLevel.SECTOR)
            .sortedBy { it.name }

    /**
     * Delete a sector (classification item) and remove all asset classifications using it.
     * Only USER-defined sectors can be deleted.
     * @return the number of affected assets
     */
    fun deleteSector(sectorCode: String): Int {
        val userStandard = standardRepository.findByKey(ClassificationService.STANDARD_USER)
        if (userStandard.isEmpty) {
            return 0
        }

        val item =
            itemRepository.findByStandardIdAndLevelAndCode(
                userStandard.get().id,
                ClassificationLevel.SECTOR,
                sectorCode
            )

        return item.map { deleteSectorItem(it) }.orElse(0)
    }

    private fun deleteSectorItem(sectorItem: ClassificationItem): Int {
        val affectedClassifications = classificationRepository.countByItemId(sectorItem.id)
        val affectedExposures = exposureRepository.countByItemId(sectorItem.id)

        // Delete all classifications and exposures using this sector
        classificationRepository.deleteByItemId(sectorItem.id)
        exposureRepository.deleteByItemId(sectorItem.id)

        // Delete the sector item itself
        itemRepository.delete(sectorItem)

        return affectedClassifications + affectedExposures
    }

    private companion object {
        val PROVIDER_STANDARDS =
            mapOf(
                ClassificationStandard.ALPHA to
                    ("AlphaVantage Sector Classification" to ClassificationStandard.PROVIDER_ALPHA),
                ClassificationStandard.EODHD to
                    ("EODHD Sector Classification" to ClassificationStandard.PROVIDER_EODHD),
                ClassificationStandard.SEC to ("SEC SIC Classification" to ClassificationStandard.PROVIDER_SEC)
            )
    }
}