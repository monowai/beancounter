package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.classification.enricher.SectorNormalizer
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for ClassificationService.
 * Tests the core business logic with mocked repositories.
 */
class ClassificationServiceTest {
    companion object {
        const val STD_ID = "std-1"
        const val STD_KEY_USER = "USER"
        const val STD_NAME_USER = "User Classification"
        const val CODE_TECH_SECTOR = "TECH_SECTOR"
    }

    private lateinit var standardRepository: ClassificationStandardRepository
    private lateinit var itemRepository: ClassificationItemRepository
    private lateinit var classificationRepository: AssetClassificationRepository
    private lateinit var exposureRepository: AssetExposureRepository
    private lateinit var sectorNormalizer: SectorNormalizer
    private lateinit var keyGenUtils: KeyGenUtils
    private lateinit var entityManager: EntityManager
    private lateinit var service: ClassificationService

    @BeforeEach
    fun setUp() {
        standardRepository = mock()
        itemRepository = mock()
        classificationRepository = mock()
        exposureRepository = mock()
        sectorNormalizer = SectorNormalizer() // Use real normalizer
        keyGenUtils = mock()
        entityManager = mock()

        service =
            ClassificationService(
                classificationRepository,
                exposureRepository,
                ClassificationTaxonomy(
                    standardRepository,
                    itemRepository,
                    classificationRepository,
                    exposureRepository,
                    sectorNormalizer,
                    keyGenUtils
                ),
                keyGenUtils = keyGenUtils,
                entityManager = entityManager
            )
        // Default key generation
        whenever(keyGenUtils.id).thenReturn("generated-id")
    }

    @Test
    fun `classifyAsset does not overwrite a manual classification with provider data`() {
        val asset = mock<Asset>()
        whenever(asset.id).thenReturn("asset-1")
        val userStandard =
            ClassificationStandard(id = STD_ID, key = STD_KEY_USER, name = STD_NAME_USER)
        val alphaStandard =
            ClassificationStandard(id = "std-alpha", key = "ALPHA", name = "Alpha")
        val manualItem =
            ClassificationItem(
                id = "item-user",
                standard = userStandard,
                level = ClassificationLevel.SECTOR,
                code = "INFORMATION_TECHNOLOGY",
                name = "Information Technology"
            )
        val alphaItem =
            ClassificationItem(
                id = "item-alpha",
                standard = alphaStandard,
                level = ClassificationLevel.SECTOR,
                code = "COMMUNICATION_SERVICES",
                name = "Communication Services"
            )
        val existingManual =
            AssetClassification(
                id = "ac-1",
                asset = asset,
                standard = userStandard,
                item = manualItem,
                level = ClassificationLevel.SECTOR,
                source = ClassificationService.SOURCE_MANUAL
            )
        whenever(
            classificationRepository.findByAssetIdAndLevel("asset-1", ClassificationLevel.SECTOR)
        ).thenReturn(Optional.of(existingManual))

        val result =
            service.classifyAsset(
                asset = asset,
                standard = alphaStandard,
                item = alphaItem,
                level = ClassificationLevel.SECTOR,
                source = AssetClassification.SOURCE_ALPHA_OVERVIEW
            )

        // User precedence: provider enrichment must not clobber a manual override.
        assertThat(result).isEqualTo(existingManual)
        verify(classificationRepository, never()).delete(any())
        verify(classificationRepository, never()).save(any())
    }

    @Test
    fun `classifyAsset replaces an existing provider classification`() {
        val asset = mock<Asset>()
        whenever(asset.id).thenReturn("asset-1")
        val alphaStandard =
            ClassificationStandard(id = "std-alpha", key = "ALPHA", name = "Alpha")
        val oldItem =
            ClassificationItem(
                id = "i-old",
                standard = alphaStandard,
                level = ClassificationLevel.SECTOR,
                code = "COMMUNICATION_SERVICES",
                name = "Communication Services"
            )
        val newItem =
            ClassificationItem(
                id = "i-new",
                standard = alphaStandard,
                level = ClassificationLevel.SECTOR,
                code = "INFORMATION_TECHNOLOGY",
                name = "Information Technology"
            )
        val existingProvider =
            AssetClassification(
                id = "ac-old",
                asset = asset,
                standard = alphaStandard,
                item = oldItem,
                level = ClassificationLevel.SECTOR,
                source = AssetClassification.SOURCE_ALPHA_OVERVIEW
            )
        whenever(
            classificationRepository.findByAssetIdAndLevel("asset-1", ClassificationLevel.SECTOR)
        ).thenReturn(Optional.of(existingProvider))
        whenever(entityManager.getReference(Asset::class.java, "asset-1")).thenReturn(asset)
        whenever(classificationRepository.save(any<AssetClassification>())).thenAnswer { it.arguments[0] }

        val result =
            service.classifyAsset(
                asset = asset,
                standard = alphaStandard,
                item = newItem,
                level = ClassificationLevel.SECTOR,
                source = AssetClassification.SOURCE_ALPHA_OVERVIEW
            )

        verify(classificationRepository).delete(existingProvider)
        assertThat(result.item).isEqualTo(newItem)
        assertThat(result.source).isEqualTo(AssetClassification.SOURCE_ALPHA_OVERVIEW)
    }

    @Test
    fun `classifyAsset allows a manual source to overwrite provider classification`() {
        val asset = mock<Asset>()
        whenever(asset.id).thenReturn("asset-1")
        val alphaStandard =
            ClassificationStandard(id = "std-alpha", key = "ALPHA", name = "Alpha")
        val userStandard =
            ClassificationStandard(id = STD_ID, key = STD_KEY_USER, name = STD_NAME_USER)
        val providerItem =
            ClassificationItem(
                id = "i-alpha",
                standard = alphaStandard,
                level = ClassificationLevel.SECTOR,
                code = "COMMUNICATION_SERVICES",
                name = "Communication Services"
            )
        val userItem =
            ClassificationItem(
                id = "i-user",
                standard = userStandard,
                level = ClassificationLevel.SECTOR,
                code = "INFORMATION_TECHNOLOGY",
                name = "Information Technology"
            )
        val existingProvider =
            AssetClassification(
                id = "ac-old",
                asset = asset,
                standard = alphaStandard,
                item = providerItem,
                level = ClassificationLevel.SECTOR,
                source = AssetClassification.SOURCE_ALPHA_OVERVIEW
            )
        whenever(
            classificationRepository.findByAssetIdAndLevel("asset-1", ClassificationLevel.SECTOR)
        ).thenReturn(Optional.of(existingProvider))
        whenever(entityManager.getReference(Asset::class.java, "asset-1")).thenReturn(asset)
        whenever(classificationRepository.save(any<AssetClassification>())).thenAnswer { it.arguments[0] }

        val result =
            service.classifyAsset(
                asset = asset,
                standard = userStandard,
                item = userItem,
                level = ClassificationLevel.SECTOR,
                source = ClassificationService.SOURCE_MANUAL
            )

        verify(classificationRepository).delete(existingProvider)
        assertThat(result.item).isEqualTo(userItem)
        assertThat(result.source).isEqualTo(ClassificationService.SOURCE_MANUAL)
    }
}