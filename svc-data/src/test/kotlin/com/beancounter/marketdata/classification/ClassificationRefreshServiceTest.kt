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
import java.util.Optional

/**
 * Unit tests for ClassificationRefreshService.
 */
class ClassificationRefreshServiceTest {
    private lateinit var assetRepository: AssetRepository
    private lateinit var assetFinder: AssetFinder
    private lateinit var classificationEnricher: AlphaClassificationEnricher
    private lateinit var service: ClassificationRefreshService

    private val testMarket = Market(code = "NASDAQ", name = "NASDAQ")

    @BeforeEach
    fun setUp() {
        assetRepository = mock()
        assetFinder = mock()
        classificationEnricher = mock()

        service =
            ClassificationRefreshService(
                assetRepository,
                assetFinder,
                classificationEnricher
            )
    }

    @Test
    fun `refreshEtfSectors processes all ETF assets`() {
        val etf1 =
            Asset(
                id = "etf-1",
                code = "VOO",
                name = "Vanguard S&P 500 ETF",
                category = "ETF",
                market = testMarket,
                status = Status.Active
            )
        val etf2 =
            Asset(
                id = "etf-2",
                code = "SCHG",
                name = "Schwab US Large-Cap Growth ETF",
                category = "ETF",
                market = testMarket,
                status = Status.Active
            )

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf1, etf2))
        whenever(assetFinder.hydrateAsset(etf1)).thenReturn(etf1)
        whenever(assetFinder.hydrateAsset(etf2)).thenReturn(etf2)
        whenever(classificationEnricher.enrichClassification(etf1)).thenReturn(true)
        whenever(classificationEnricher.enrichClassification(etf2)).thenReturn(true)

        val result = service.refreshEtfSectors()

        assertThat(result.total).isEqualTo(2)
        assertThat(result.processed).isEqualTo(2)
        assertThat(result.errors).isEqualTo(0)
    }

    @Test
    fun `refreshEtfSectors handles enrichment failures`() {
        val etf =
            Asset(
                id = "etf-1",
                code = "VOO",
                name = "Vanguard S&P 500 ETF",
                category = "ETF",
                market = testMarket,
                status = Status.Active
            )

        whenever(assetRepository.findActiveEtfs()).thenReturn(listOf(etf))
        whenever(assetFinder.hydrateAsset(etf)).thenReturn(etf)
        whenever(classificationEnricher.enrichClassification(etf))
            .thenThrow(RuntimeException("API error"))

        val result = service.refreshEtfSectors()

        assertThat(result.total).isEqualTo(1)
        assertThat(result.processed).isEqualTo(0)
        assertThat(result.errors).isEqualTo(1)
    }

    @Test
    fun `refreshEquityClassifications processes all Equity assets`() {
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
        whenever(assetFinder.hydrateAsset(equity)).thenReturn(equity)
        whenever(classificationEnricher.enrichClassification(equity)).thenReturn(true)

        val result = service.refreshEquityClassifications()

        assertThat(result.total).isEqualTo(1)
        assertThat(result.processed).isEqualTo(1)
        assertThat(result.errors).isEqualTo(0)
    }

    @Test
    fun `refreshAsset returns false when asset not found`() {
        whenever(assetRepository.findById("unknown-id")).thenReturn(Optional.empty())

        val result = service.refreshAsset("unknown-id")

        assertThat(result).isFalse()
        verify(classificationEnricher, never()).enrichClassification(any())
    }

    @Test
    fun `refreshAsset enriches found asset`() {
        val asset =
            Asset(
                id = "asset-1",
                code = "SCHG",
                name = "Schwab Growth",
                category = "ETF",
                market = testMarket,
                status = Status.Active
            )

        whenever(assetRepository.findById("asset-1")).thenReturn(Optional.of(asset))
        whenever(assetFinder.hydrateAsset(asset)).thenReturn(asset)
        whenever(classificationEnricher.enrichClassification(asset)).thenReturn(true)

        val result = service.refreshAsset("asset-1")

        assertThat(result).isTrue()
        verify(classificationEnricher).enrichClassification(asset)
    }

    @Test
    fun `refreshAssetByCode returns false when asset not found`() {
        whenever(assetRepository.findByMarketCodeAndCode("NASDAQ", "UNKNOWN"))
            .thenReturn(Optional.empty())

        val result = service.refreshAssetByCode("NASDAQ", "UNKNOWN")

        assertThat(result).isFalse()
        verify(classificationEnricher, never()).enrichClassification(any())
    }

    @Test
    fun `refreshAssetByCode enriches found asset`() {
        val asset =
            Asset(
                id = "asset-1",
                code = "SCHG",
                name = "Schwab Growth",
                category = "ETF",
                market = testMarket,
                status = Status.Active
            )

        whenever(assetRepository.findByMarketCodeAndCode("NASDAQ", "SCHG"))
            .thenReturn(Optional.of(asset))
        whenever(assetFinder.hydrateAsset(asset)).thenReturn(asset)
        whenever(classificationEnricher.enrichClassification(asset)).thenReturn(true)

        val result = service.refreshAssetByCode("NASDAQ", "SCHG")

        assertThat(result).isTrue()
        verify(classificationEnricher).enrichClassification(asset)
    }
}