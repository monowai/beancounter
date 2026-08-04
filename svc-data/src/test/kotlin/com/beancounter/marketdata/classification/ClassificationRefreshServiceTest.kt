package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.assets.AssetRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [ClassificationRefreshService].
 *
 * Covers the resume-aware refresh design (bc-claude fix/classification-refresh-resume): the
 * staleness window, the per-run batch limit, aborting on the first [EnrichmentResult.RATE_LIMITED],
 * `classificationCheckedAt` stamping, and honest [com.beancounter.common.contracts.BackfillResult]
 * counts - specifically the regression that used to let [EnrichmentResult.FAILED] outcomes vanish
 * silently instead of landing in `errors`.
 */
class ClassificationRefreshServiceTest {
    private lateinit var assetRepository: AssetRepository
    private lateinit var classificationEnricher: ClassificationEnricher

    private val testMarket = Market(code = "NASDAQ", name = "NASDAQ")

    private fun service(batchLimit: Int = 20) =
        ClassificationRefreshService(
            assetRepository,
            classificationEnricher,
            staleAfterDays = 7,
            batchLimit = batchLimit
        )

    private fun etf(id: String) =
        Asset(
            id = id,
            code = id,
            name = "ETF $id",
            category = "ETF",
            market = testMarket,
            status = Status.Active
        )

    @BeforeEach
    fun setUp() {
        assetRepository = mock()
        classificationEnricher = mock()
        whenever(assetRepository.save(any<Asset>())).thenAnswer { it.getArgument<Asset>(0) }
    }

    @Test
    fun `refreshEtfSectors processes all due ETF assets`() {
        val etf1 = etf("etf-1")
        val etf2 = etf("etf-2")

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf1, etf2))
        whenever(assetRepository.findEtfsDueForClassification(any())).thenReturn(listOf(etf1, etf2))
        whenever(classificationEnricher.enrichClassification(etf1)).thenReturn(EnrichmentResult.ENRICHED)
        whenever(classificationEnricher.enrichClassification(etf2)).thenReturn(EnrichmentResult.ENRICHED)

        val result = service().refreshEtfSectors()

        assertThat(result.total).isEqualTo(2)
        assertThat(result.processed).isEqualTo(2)
        assertThat(result.errors).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
    }

    @Test
    fun `refreshEtfSectors handles enrichment exceptions as errors`() {
        val etf1 = etf("etf-1")

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf1))
        whenever(assetRepository.findEtfsDueForClassification(any())).thenReturn(listOf(etf1))
        whenever(classificationEnricher.enrichClassification(etf1))
            .thenThrow(RuntimeException("API error"))

        val result = service().refreshEtfSectors()

        assertThat(result.total).isEqualTo(1)
        assertThat(result.processed).isEqualTo(0)
        assertThat(result.errors).isEqualTo(1)
    }

    @Test
    fun `refreshEquityClassifications processes all due Equity assets`() {
        val equity =
            Asset(
                id = "eq-1",
                code = "AAPL",
                name = "Apple Inc",
                category = "EQUITY",
                market = testMarket,
                status = Status.Active
            )

        whenever(assetRepository.findActiveEquities()).thenReturn(listOf(equity))
        whenever(assetRepository.findEquitiesDueForClassification(any())).thenReturn(listOf(equity))
        whenever(classificationEnricher.enrichClassification(equity)).thenReturn(EnrichmentResult.ENRICHED)

        val result = service().refreshEquityClassifications()

        assertThat(result.total).isEqualTo(1)
        assertThat(result.processed).isEqualTo(1)
        assertThat(result.errors).isEqualTo(0)
    }

    @Test
    fun `refreshAsset returns FAILED when asset not found`() {
        whenever(assetRepository.findById("unknown-id")).thenReturn(Optional.empty())

        val result = service().refreshAsset("unknown-id")

        assertThat(result).isEqualTo(EnrichmentResult.FAILED)
        verify(classificationEnricher, never()).enrichClassification(any())
    }

    @Test
    fun `refreshAsset enriches found asset and stamps checked-at`() {
        val asset = etf("asset-1")

        whenever(assetRepository.findById("asset-1")).thenReturn(Optional.of(asset))
        whenever(classificationEnricher.enrichClassification(asset)).thenReturn(EnrichmentResult.ENRICHED)

        val result = service().refreshAsset("asset-1")

        assertThat(result).isEqualTo(EnrichmentResult.ENRICHED)
        verify(classificationEnricher).enrichClassification(asset)
        assertThat(asset.classificationCheckedAt).isNotNull()
    }

    @Test
    fun `refreshAssetByCode returns FAILED when asset not found`() {
        whenever(assetRepository.findByMarketCodeAndCode("NASDAQ", "UNKNOWN"))
            .thenReturn(Optional.empty())

        val result = service().refreshAssetByCode("NASDAQ", "UNKNOWN")

        assertThat(result).isEqualTo(EnrichmentResult.FAILED)
        verify(classificationEnricher, never()).enrichClassification(any())
    }

    @Test
    fun `refreshAssetByCode enriches found asset`() {
        val asset = etf("asset-1")

        whenever(assetRepository.findByMarketCodeAndCode("NASDAQ", "SCHG"))
            .thenReturn(Optional.of(asset))
        whenever(classificationEnricher.enrichClassification(asset)).thenReturn(EnrichmentResult.ENRICHED)

        val result = service().refreshAssetByCode("NASDAQ", "SCHG")

        assertThat(result).isEqualTo(EnrichmentResult.ENRICHED)
        verify(classificationEnricher).enrichClassification(asset)
    }

    @Test
    fun `assets inside the staleness window are skipped and not attempted`() {
        val due = etf("etf-due")
        val staleWindow1 = etf("etf-fresh-1")
        val staleWindow2 = etf("etf-fresh-2")

        // findActiveEtfs = full population (3); findEtfsDueForClassification = only the stale one.
        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(due, staleWindow1, staleWindow2))
        whenever(assetRepository.findEtfsDueForClassification(any())).thenReturn(listOf(due))
        whenever(classificationEnricher.enrichClassification(due)).thenReturn(EnrichmentResult.ENRICHED)

        val result = service().refreshEtfSectors()

        assertThat(result.total).isEqualTo(3)
        assertThat(result.processed).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
        verify(classificationEnricher, never()).enrichClassification(staleWindow1)
        verify(classificationEnricher, never()).enrichClassification(staleWindow2)
    }

    @Test
    fun `attempts stop at the batch limit and the remainder is skipped`() {
        val etf1 = etf("etf-1")
        val etf2 = etf("etf-2")
        val etf3 = etf("etf-3")

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf1, etf2, etf3))
        whenever(assetRepository.findEtfsDueForClassification(any())).thenReturn(listOf(etf1, etf2, etf3))
        whenever(classificationEnricher.enrichClassification(etf1)).thenReturn(EnrichmentResult.ENRICHED)
        whenever(classificationEnricher.enrichClassification(etf2)).thenReturn(EnrichmentResult.ENRICHED)

        val result = service(batchLimit = 2).refreshEtfSectors()

        assertThat(result.total).isEqualTo(3)
        assertThat(result.processed).isEqualTo(2)
        assertThat(result.skipped).isEqualTo(1)
        verify(classificationEnricher, never()).enrichClassification(etf3)
    }

    @Test
    fun `a RATE_LIMITED result aborts the run and later assets are not attempted`() {
        val etf1 = etf("etf-1")
        val etf2 = etf("etf-2")
        val etf3 = etf("etf-3")

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf1, etf2, etf3))
        whenever(assetRepository.findEtfsDueForClassification(any())).thenReturn(listOf(etf1, etf2, etf3))
        whenever(classificationEnricher.enrichClassification(etf1)).thenReturn(EnrichmentResult.ENRICHED)
        whenever(classificationEnricher.enrichClassification(etf2)).thenReturn(EnrichmentResult.RATE_LIMITED)

        val result = service().refreshEtfSectors()

        assertThat(result.total).isEqualTo(3)
        assertThat(result.processed).isEqualTo(1)
        assertThat(result.rateLimited).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
        verify(classificationEnricher, never()).enrichClassification(etf3)
    }

    @Test
    fun `FAILED results are counted in errors, not silently dropped`() {
        val etf1 = etf("etf-1")

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf1))
        whenever(assetRepository.findEtfsDueForClassification(any())).thenReturn(listOf(etf1))
        whenever(classificationEnricher.enrichClassification(etf1)).thenReturn(EnrichmentResult.FAILED)

        val result = service().refreshEtfSectors()

        assertThat(result.errors).isEqualTo(1)
        assertThat(result.processed).isEqualTo(0)
        assertThat(result.rateLimited).isEqualTo(0)
    }

    @Test
    fun `checked-at is stamped for ENRICHED, NO_DATA and FAILED but not RATE_LIMITED`() {
        val enriched = etf("etf-enriched")
        val noData = etf("etf-nodata")
        val failed = etf("etf-failed")
        val rateLimited = etf("etf-ratelimited")

        whenever(assetRepository.findActiveEtfs())
            .thenReturn(listOf(enriched, noData, failed, rateLimited))
        whenever(assetRepository.findEtfsDueForClassification(any()))
            .thenReturn(listOf(enriched, noData, failed, rateLimited))
        whenever(classificationEnricher.enrichClassification(enriched)).thenReturn(EnrichmentResult.ENRICHED)
        whenever(classificationEnricher.enrichClassification(noData)).thenReturn(EnrichmentResult.NO_DATA)
        whenever(classificationEnricher.enrichClassification(failed)).thenReturn(EnrichmentResult.FAILED)
        whenever(classificationEnricher.enrichClassification(rateLimited)).thenReturn(EnrichmentResult.RATE_LIMITED)

        service().refreshEtfSectors()

        assertThat(enriched.classificationCheckedAt).isNotNull()
        assertThat(noData.classificationCheckedAt).isNotNull()
        assertThat(failed.classificationCheckedAt).isNotNull()
        assertThat(rateLimited.classificationCheckedAt).isNull()

        val saved = argumentCaptor<Asset>()
        verify(assetRepository, times(3)).save(saved.capture())
        assertThat(saved.allValues.map { it.id })
            .containsExactlyInAnyOrder("etf-enriched", "etf-nodata", "etf-failed")
    }
}