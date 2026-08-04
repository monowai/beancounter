package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [ClassificationController].
 *
 * Covers the adjacent fix found alongside the resume-aware refresh work: `/backfill` used to call
 * [ClassificationEnricher] directly, so a manually-backfilled asset never got its
 * `classificationCheckedAt` stamped and would look "never checked" to the resume-aware scheduled
 * refresh - wasting quota re-attempting it. `/backfill` now goes through
 * [ClassificationRefreshService.attempt], which stamps it.
 */
class ClassificationControllerTest {
    private lateinit var classificationEnricher: ClassificationEnricher
    private lateinit var classificationService: ClassificationService
    private lateinit var classificationRefreshService: ClassificationRefreshService
    private lateinit var assetRepository: AssetRepository
    private lateinit var assetFinder: AssetFinder
    private lateinit var controller: ClassificationController

    private val market = Market(code = "NASDAQ", name = "NASDAQ")

    private fun etf(id: String) =
        Asset(id = id, code = id, name = "ETF $id", category = "ETF", market = market, status = Status.Active)

    @BeforeEach
    fun setUp() {
        classificationEnricher = mock()
        classificationService = mock()
        classificationRefreshService = mock()
        assetRepository = mock()
        assetFinder = mock()
        controller =
            ClassificationController(
                classificationEnricher,
                classificationService,
                classificationRefreshService,
                assetRepository,
                assetFinder
            )

        whenever(classificationEnricher.canEnrich(any())).thenReturn(true)
        whenever(classificationEnricher.isEtf(any())).thenReturn(true)
        whenever(classificationService.hasExposures(any())).thenReturn(false)
    }

    @Test
    fun `backfillClassifications delegates to the refresh service so checked-at is stamped`() {
        val asset = etf("etf-1")
        whenever(assetRepository.findAll()).thenReturn(listOf(asset))
        whenever(classificationRefreshService.attempt(asset)).thenReturn(EnrichmentResult.ENRICHED)

        val response = controller.backfillClassifications()

        assertThat(response.data.processed).isEqualTo(1)
        assertThat(response.data.errors).isEqualTo(0)
        verify(classificationRefreshService).attempt(asset)
        verify(classificationEnricher, never()).enrichClassification(any())
    }

    @Test
    fun `backfillClassifications counts NO_DATA and RATE_LIMITED separately from processed and errors`() {
        val noData = etf("etf-nodata")
        val rateLimited = etf("etf-ratelimited")
        whenever(assetRepository.findAll()).thenReturn(listOf(noData, rateLimited))
        whenever(classificationRefreshService.attempt(noData)).thenReturn(EnrichmentResult.NO_DATA)
        whenever(classificationRefreshService.attempt(rateLimited)).thenReturn(EnrichmentResult.RATE_LIMITED)

        val response = controller.backfillClassifications()

        assertThat(response.data.processed).isEqualTo(0)
        assertThat(response.data.errors).isEqualTo(0)
        assertThat(response.data.noData).isEqualTo(1)
        assertThat(response.data.rateLimited).isEqualTo(1)
    }

    @Test
    fun `refreshAsset reports both refreshed and result for a rate-limited outcome`() {
        whenever(classificationRefreshService.refreshAsset("asset-1")).thenReturn(EnrichmentResult.RATE_LIMITED)

        val response = controller.refreshAsset("asset-1")

        assertThat(response["refreshed"]).isEqualTo(false)
        assertThat(response["result"]).isEqualTo("RATE_LIMITED")
    }

    @Test
    fun `refreshAssetByCode reports refreshed true and result ENRICHED on success`() {
        whenever(classificationRefreshService.refreshAssetByCode("NASDAQ", "SCHG"))
            .thenReturn(EnrichmentResult.ENRICHED)

        val response = controller.refreshAssetByCode("NASDAQ", "SCHG")

        assertThat(response["refreshed"]).isEqualTo(true)
        assertThat(response["result"]).isEqualTo("ENRICHED")
    }
}