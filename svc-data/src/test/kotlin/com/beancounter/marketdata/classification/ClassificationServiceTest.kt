package com.beancounter.marketdata.classification

import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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
    private lateinit var holdingRepository: AssetHoldingRepository
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
        holdingRepository = mock()

        service =
            ClassificationService(
                standardRepository,
                itemRepository,
                classificationRepository,
                exposureRepository,
                holdingRepository = holdingRepository,
                keyGenUtils = keyGenUtils,
                sectorNormalizer = sectorNormalizer,
                entityManager = entityManager
            )
        // Default key generation
        whenever(keyGenUtils.id).thenReturn("generated-id")
    }

    @Test
    fun `getOrCreateStandard returns existing standard`() {
        val existingStandard =
            ClassificationStandard(
                id = STD_ID,
                key = "ALPHA",
                name = "AlphaVantage",
                version = "1.0",
                provider = "ALPHAVANTAGE"
            )
        whenever(standardRepository.findByKey("ALPHA")).thenReturn(Optional.of(existingStandard))

        val result = service.getOrCreateStandard("ALPHA", "AlphaVantage")

        assertThat(result).isEqualTo(existingStandard)
        verify(standardRepository, never()).save(any())
    }

    @Test
    fun `getOrCreateStandard creates new standard when not found`() {
        whenever(standardRepository.findByKey("NEW")).thenReturn(Optional.empty())
        whenever(standardRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = service.getOrCreateStandard("NEW", "New Standard", "2.0", "PROVIDER")

        assertThat(result.key).isEqualTo("NEW")
        assertThat(result.name).isEqualTo("New Standard")
        assertThat(result.version).isEqualTo("2.0")
        assertThat(result.provider).isEqualTo("PROVIDER")

        val captor = ArgumentCaptor.forClass(ClassificationStandard::class.java)
        verify(standardRepository).save(captor.capture())
        assertThat(captor.value.id).isEqualTo("generated-id")
    }

    @Test
    fun `getOrCreateItem creates item with name and calculates code`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = STD_KEY_USER,
                name = STD_NAME_USER
            )
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.SECTOR,
                "MULTI_SECTOR"
            )
        ).thenReturn(Optional.empty())
        whenever(itemRepository.save(any())).thenAnswer { it.arguments[0] }

        val result =
            service.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                name = "Multi Sector"
            )

        assertThat(result.code).isEqualTo("MULTI_SECTOR")
        assertThat(result.name).isEqualTo("Multi Sector")
        assertThat(result.level).isEqualTo(ClassificationLevel.SECTOR)
    }

    @Test
    fun `getOrCreateItem normalizes rawCode for sectors`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = "ALPHA",
                name = "Alpha Classification"
            )
        // "TECHNOLOGY" normalizes to "Information Technology" -> code "INFORMATION_TECHNOLOGY"
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.SECTOR,
                "INFORMATION_TECHNOLOGY"
            )
        ).thenReturn(Optional.empty())
        whenever(itemRepository.save(any())).thenAnswer { it.arguments[0] }

        val result =
            service.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                rawCode = "TECHNOLOGY"
            )

        assertThat(result.name).isEqualTo("Information Technology")
        assertThat(result.code).isEqualTo("INFORMATION_TECHNOLOGY")
    }

    @Test
    fun `getOrCreateItem returns existing item when found`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = STD_KEY_USER,
                name = STD_NAME_USER
            )
        val existingItem =
            ClassificationItem(
                id = "item-1",
                standard = standard,
                level = ClassificationLevel.SECTOR,
                code = CODE_TECH_SECTOR,
                name = "Tech Sector"
            )
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.SECTOR,
                CODE_TECH_SECTOR
            )
        ).thenReturn(Optional.of(existingItem))

        val result =
            service.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                name = "Tech Sector"
            )

        assertThat(result).isEqualTo(existingItem)
        verify(itemRepository, never()).save(any())
    }

    @Test
    fun `getOrCreateItem updates name if it differs`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = STD_KEY_USER,
                name = STD_NAME_USER
            )
        val existingItem =
            ClassificationItem(
                id = "item-1",
                standard = standard,
                level = ClassificationLevel.SECTOR,
                code = CODE_TECH_SECTOR,
                name = "Old Tech Sector"
            )
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.SECTOR,
                CODE_TECH_SECTOR
            )
        ).thenReturn(Optional.of(existingItem))
        whenever(itemRepository.save(any())).thenAnswer { it.arguments[0] }

        val result =
            service.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                name = "Tech Sector"
            )

        assertThat(result.name).isEqualTo("Tech Sector")
        verify(itemRepository).save(existingItem)
    }

    @Test
    fun `getOrCreateItem throws when neither name nor rawCode provided`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = STD_KEY_USER,
                name = STD_NAME_USER
            )

        assertThatThrownBy {
            service.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Either name or rawCode must be provided")
    }

    @Test
    fun `getOrCreateItem uses rawCode directly for non-sector levels`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = "ALPHA",
                name = "Alpha Classification"
            )
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.INDUSTRY,
                "CONSUMER_ELECTRONICS"
            )
        ).thenReturn(Optional.empty())
        whenever(itemRepository.save(any())).thenAnswer { it.arguments[0] }

        val result =
            service.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.INDUSTRY,
                rawCode = "Consumer Electronics"
            )

        // For INDUSTRY, rawCode is used directly (not normalized through SectorNormalizer)
        assertThat(result.name).isEqualTo("Consumer Electronics")
        assertThat(result.code).isEqualTo("CONSUMER_ELECTRONICS")
    }

    @Test
    fun `deleteSector returns zero when standard not found`() {
        whenever(standardRepository.findByKey(ClassificationService.STANDARD_USER))
            .thenReturn(Optional.empty())

        val result = service.deleteSector("SOME_SECTOR")

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `deleteSector returns zero when sector not found`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = STD_KEY_USER,
                name = STD_NAME_USER
            )
        whenever(standardRepository.findByKey(ClassificationService.STANDARD_USER))
            .thenReturn(Optional.of(standard))
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.SECTOR,
                "NONEXISTENT"
            )
        ).thenReturn(Optional.empty())

        val result = service.deleteSector("NONEXISTENT")

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `deleteSector removes sector and returns affected count`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = STD_KEY_USER,
                name = STD_NAME_USER
            )
        val sectorItem =
            ClassificationItem(
                id = "item-1",
                standard = standard,
                level = ClassificationLevel.SECTOR,
                code = "CUSTOM_SECTOR",
                name = "Custom Sector"
            )

        whenever(standardRepository.findByKey(ClassificationService.STANDARD_USER))
            .thenReturn(Optional.of(standard))
        whenever(
            itemRepository.findByStandardIdAndLevelAndCode(
                STD_ID,
                ClassificationLevel.SECTOR,
                "CUSTOM_SECTOR"
            )
        ).thenReturn(Optional.of(sectorItem))
        whenever(classificationRepository.countByItemId("item-1")).thenReturn(5)
        whenever(exposureRepository.countByItemId("item-1")).thenReturn(3)

        val result = service.deleteSector("CUSTOM_SECTOR")

        assertThat(result).isEqualTo(8) // 5 classifications + 3 exposures
        verify(classificationRepository).deleteByItemId("item-1")
        verify(exposureRepository).deleteByItemId("item-1")
        verify(itemRepository).delete(sectorItem)
    }

    @Test
    fun `getAllSectors returns sorted list`() {
        val standard =
            ClassificationStandard(
                id = STD_ID,
                key = "ALPHA",
                name = "Alpha"
            )
        val sectors =
            listOf(
                ClassificationItem(
                    id = "1",
                    standard = standard,
                    level = ClassificationLevel.SECTOR,
                    code = "ENERGY",
                    name = "Energy"
                ),
                ClassificationItem(
                    id = "2",
                    standard = standard,
                    level = ClassificationLevel.SECTOR,
                    code = "BASIC_MATERIALS",
                    name = "Basic Materials"
                )
            )
        whenever(itemRepository.findByLevel(ClassificationLevel.SECTOR)).thenReturn(sectors)

        val result = service.getAllSectors()

        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("Basic Materials")
        assertThat(result[1].name).isEqualTo("Energy")
    }

    @Test
    fun `clearExposures delegates to repository`() {
        service.clearExposures("asset-123")

        verify(exposureRepository).deleteByAssetId("asset-123")
    }

    @Test
    fun `hasClassifications returns true when classifications exist`() {
        whenever(classificationRepository.findByAssetId("asset-1"))
            .thenReturn(listOf(mock()))

        assertThat(service.hasClassifications("asset-1")).isTrue()
    }

    @Test
    fun `hasClassifications returns false when no classifications exist`() {
        whenever(classificationRepository.findByAssetId("asset-1"))
            .thenReturn(emptyList())

        assertThat(service.hasClassifications("asset-1")).isFalse()
    }

    @Test
    fun `hasExposures returns true when exposures exist`() {
        whenever(exposureRepository.findByAssetId("asset-1"))
            .thenReturn(listOf(mock()))

        assertThat(service.hasExposures("asset-1")).isTrue()
    }

    @Test
    fun `hasExposures returns false when no exposures exist`() {
        whenever(exposureRepository.findByAssetId("asset-1"))
            .thenReturn(emptyList())

        assertThat(service.hasExposures("asset-1")).isFalse()
    }
}