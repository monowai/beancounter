package com.beancounter.position.service

import com.beancounter.client.services.MarketDataClient
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.AssetExposure
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Tests for SectorExposureService covering:
 * - Empty positions handling
 * - Zero total value handling
 * - ETF sector exposure distribution
 * - Equity direct sector classification
 * - Other asset category handling (Cash, etc.)
 * - Mixed portfolio calculations
 * - API error handling
 * - Edge cases with negative/zero market values
 */
@ExtendWith(MockitoExtension::class)
class SectorExposureServiceTest {
    @Mock
    private lateinit var marketDataClient: MarketDataClient

    private lateinit var sectorExposureService: SectorExposureService
    private val portfolio = TestHelpers.createTestPortfolio("SectorExposureTest")
    private val testStandard =
        ClassificationStandard(
            id = "std-1",
            key = ClassificationStandard.ALPHA,
            name = "AlphaVantage Sectors"
        )

    @BeforeEach
    fun setup() {
        sectorExposureService = SectorExposureService(marketDataClient)
    }

    @Test
    fun `empty positions returns empty sector exposure data`() {
        // Given - positions with no positions added
        val positions = Positions(portfolio)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).isEmpty()
        assertThat(result.totalValue).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `zero total value returns empty exposures with currency`() {
        // Given - positions with zero total value
        val positions = Positions(portfolio)
        val asset = createAssetWithCategory("ZERO_ASSET", AssetCategory.REPORT_EQUITY)
        val position = Position(asset, portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(portfolio.currency).apply {
                marketValue = BigDecimal.ZERO
            }
        positions.add(position)

        // Set up totals with zero market value
        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal.ZERO
        positions.setTotal(Position.In.PORTFOLIO, totals)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).isEmpty()
        assertThat(result.currency).isEqualTo(portfolio.currency.code)
    }

    @Test
    fun `ETF distributes market value by sector weights`() {
        // Given - an ETF with sector exposures
        val etfAsset = createAssetWithCategory("VOO", AssetCategory.REPORT_ETF)
        val positions = createPositionsWithAsset(etfAsset, BigDecimal("10000.00"))

        // Mock ETF exposures: 40% Tech, 30% Healthcare, 30% Financials
        val exposures =
            listOf(
                createAssetExposure(etfAsset, "Information Technology", BigDecimal("40.00")),
                createAssetExposure(etfAsset, "Health Care", BigDecimal("30.00")),
                createAssetExposure(etfAsset, "Financials", BigDecimal("30.00"))
            )
        whenever(marketDataClient.getExposures(etfAsset.id)).thenReturn(exposures)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(3)
        assertThat(result.totalValue).isEqualByComparingTo(BigDecimal("10000.00"))

        val techExposure = result.exposures.find { it.sector == "Information Technology" }
        assertThat(techExposure).isNotNull
        assertThat(techExposure!!.marketValue).isEqualByComparingTo(BigDecimal("4000.00"))
        assertThat(techExposure.percentage).isEqualByComparingTo(BigDecimal("40.00"))

        val healthExposure = result.exposures.find { it.sector == "Health Care" }
        assertThat(healthExposure).isNotNull
        assertThat(healthExposure!!.marketValue).isEqualByComparingTo(BigDecimal("3000.00"))
    }

    @Test
    fun `ETF with no exposures is classified as Unclassified`() {
        // Given - an ETF with no exposure data
        val etfAsset = createAssetWithCategory("UNKNOWN_ETF", AssetCategory.REPORT_ETF)
        val positions = createPositionsWithAsset(etfAsset, BigDecimal("5000.00"))

        whenever(marketDataClient.getExposures(etfAsset.id)).thenReturn(emptyList())

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(1)
        val unclassified = result.exposures.first()
        assertThat(unclassified.sector).isEqualTo(ClassificationItem.UNCLASSIFIED)
        assertThat(unclassified.marketValue).isEqualByComparingTo(BigDecimal("5000.00"))
        assertThat(unclassified.percentage).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `ETF API failure results in Unclassified`() {
        // Given - an ETF where API call fails
        val etfAsset = createAssetWithCategory("FAIL_ETF", AssetCategory.REPORT_ETF)
        val positions = createPositionsWithAsset(etfAsset, BigDecimal("5000.00"))

        whenever(marketDataClient.getExposures(etfAsset.id))
            .thenThrow(RestClientException("API unavailable"))

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(1)
        assertThat(result.exposures.first().sector).isEqualTo(ClassificationItem.UNCLASSIFIED)
    }

    @Test
    fun `Equity is classified to its sector`() {
        // Given - an equity with sector classification
        val equityAsset = createAssetWithCategory("AAPL", AssetCategory.REPORT_EQUITY)
        val positions = createPositionsWithAsset(equityAsset, BigDecimal("15000.00"))

        val classification =
            createAssetClassification(
                equityAsset,
                ClassificationLevel.SECTOR,
                "Information Technology"
            )
        whenever(marketDataClient.getClassifications(equityAsset.id)).thenReturn(listOf(classification))

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(1)
        val techExposure = result.exposures.first()
        assertThat(techExposure.sector).isEqualTo("Information Technology")
        assertThat(techExposure.marketValue).isEqualByComparingTo(BigDecimal("15000.00"))
        assertThat(techExposure.percentage).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `Equity with no sector classification is Unclassified`() {
        // Given - an equity with only industry classification (no sector)
        val equityAsset = createAssetWithCategory("NEW_STOCK", AssetCategory.REPORT_EQUITY)
        val positions = createPositionsWithAsset(equityAsset, BigDecimal("8000.00"))

        // Return only industry-level classification
        val industryClassification =
            createAssetClassification(
                equityAsset,
                ClassificationLevel.INDUSTRY,
                "Consumer Electronics"
            )
        whenever(marketDataClient.getClassifications(equityAsset.id))
            .thenReturn(listOf(industryClassification))

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(1)
        assertThat(result.exposures.first().sector).isEqualTo(ClassificationItem.UNCLASSIFIED)
    }

    @Test
    fun `Equity API failure results in Unclassified`() {
        // Given - an equity where classification API fails
        val equityAsset = createAssetWithCategory("FAIL_STOCK", AssetCategory.REPORT_EQUITY)
        val positions = createPositionsWithAsset(equityAsset, BigDecimal("5000.00"))

        whenever(marketDataClient.getClassifications(equityAsset.id))
            .thenThrow(RestClientException("Classification service down"))

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(1)
        assertThat(result.exposures.first().sector).isEqualTo(ClassificationItem.UNCLASSIFIED)
    }

    @Test
    fun `Cash is grouped by its report category`() {
        // Given - a cash position
        val cashAsset = createAssetWithCategory("USD_CASH", AssetCategory.REPORT_CASH)
        val positions = createPositionsWithAsset(cashAsset, BigDecimal("2000.00"))

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.exposures).hasSize(1)
        assertThat(result.exposures.first().sector).isEqualTo(AssetCategory.REPORT_CASH)
        assertThat(result.exposures.first().marketValue).isEqualByComparingTo(BigDecimal("2000.00"))
    }

    @Test
    fun `mixed portfolio aggregates by sector correctly`() {
        // Given - a portfolio with ETF, Equity, and Cash
        val positions = Positions(portfolio)

        // ETF: $10,000 with 50% Tech, 50% Healthcare
        val etfAsset = createAssetWithCategory("VTI", AssetCategory.REPORT_ETF)
        addPositionToPositions(positions, etfAsset, BigDecimal("10000.00"))

        val etfExposures =
            listOf(
                createAssetExposure(etfAsset, "Information Technology", BigDecimal("50.00")),
                createAssetExposure(etfAsset, "Health Care", BigDecimal("50.00"))
            )
        whenever(marketDataClient.getExposures(etfAsset.id)).thenReturn(etfExposures)

        // Equity: $5,000 in Tech
        val equityAsset = createAssetWithCategory("MSFT", AssetCategory.REPORT_EQUITY)
        addPositionToPositions(positions, equityAsset, BigDecimal("5000.00"))

        val equityClassification =
            createAssetClassification(
                equityAsset,
                ClassificationLevel.SECTOR,
                "Information Technology"
            )
        whenever(marketDataClient.getClassifications(equityAsset.id))
            .thenReturn(listOf(equityClassification))

        // Cash: $2,000
        val cashAsset = createAssetWithCategory("CASH", AssetCategory.REPORT_CASH)
        addPositionToPositions(positions, cashAsset, BigDecimal("2000.00"))

        // Set total value: $17,000
        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("17000.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then
        assertThat(result.totalValue).isEqualByComparingTo(BigDecimal("17000.00"))
        assertThat(result.exposures).hasSize(3)

        // Tech: $5,000 (ETF) + $5,000 (Equity) = $10,000 = 58.82%
        val techExposure = result.exposures.find { it.sector == "Information Technology" }
        assertThat(techExposure).isNotNull
        assertThat(techExposure!!.marketValue).isEqualByComparingTo(BigDecimal("10000.00"))
        assertThat(techExposure.percentage).isEqualByComparingTo(BigDecimal("58.82"))

        // Healthcare: $5,000 (from ETF) = 29.41%
        val healthExposure = result.exposures.find { it.sector == "Health Care" }
        assertThat(healthExposure).isNotNull
        assertThat(healthExposure!!.marketValue).isEqualByComparingTo(BigDecimal("5000.00"))

        // Cash: $2,000 = 11.76%
        val cashExposure = result.exposures.find { it.sector == AssetCategory.REPORT_CASH }
        assertThat(cashExposure).isNotNull
        assertThat(cashExposure!!.marketValue).isEqualByComparingTo(BigDecimal("2000.00"))
    }

    @Test
    fun `exposures are sorted by percentage descending`() {
        // Given - multiple equities in different sectors
        val positions = Positions(portfolio)

        val sectors =
            listOf(
                "Financials" to BigDecimal("1000.00"),
                "Health Care" to BigDecimal("3000.00"),
                "Information Technology" to BigDecimal("5000.00"),
                "Energy" to BigDecimal("1000.00")
            )

        sectors.forEachIndexed { idx, (sector, value) ->
            val asset = createAssetWithCategory("STOCK_$idx", AssetCategory.REPORT_EQUITY)
            addPositionToPositions(positions, asset, value)
            val classification = createAssetClassification(asset, ClassificationLevel.SECTOR, sector)
            whenever(marketDataClient.getClassifications(asset.id)).thenReturn(listOf(classification))
        }

        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("10000.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then - should be sorted by percentage descending
        assertThat(result.exposures).hasSize(4)
        assertThat(result.exposures[0].sector).isEqualTo("Information Technology")
        assertThat(result.exposures[1].sector).isEqualTo("Health Care")
        // Financials and Energy both have 10%, order depends on implementation
    }

    @Test
    fun `positions with zero or negative market value are skipped`() {
        // Given - positions with various market values
        val positions = Positions(portfolio)

        // Position with positive value
        val goodAsset = createAssetWithCategory("GOOD", AssetCategory.REPORT_CASH)
        addPositionToPositions(positions, goodAsset, BigDecimal("5000.00"))

        // Position with zero value
        val zeroAsset = createAssetWithCategory("ZERO", AssetCategory.REPORT_CASH)
        addPositionToPositions(positions, zeroAsset, BigDecimal.ZERO)

        // Position with negative value (shouldn't happen normally, but defensive)
        val negativeAsset = createAssetWithCategory("NEG", AssetCategory.REPORT_CASH)
        addPositionToPositions(positions, negativeAsset, BigDecimal("-100.00"))

        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("5000.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions)

        // Then - only positive value position contributes
        assertThat(result.exposures).hasSize(1)
        assertThat(result.exposures.first().marketValue).isEqualByComparingTo(BigDecimal("5000.00"))
    }

    @Test
    fun `uses BASE currency when requested`() {
        // Given - positions with BASE totals set
        val positions = Positions(portfolio)
        val asset = createAssetWithCategory("ASSET", AssetCategory.REPORT_CASH)
        val position = Position(asset, portfolio)
        position.moneyValues[Position.In.BASE] =
            MoneyValues(portfolio.base).apply {
                marketValue = BigDecimal("10000.00")
            }
        positions.add(position)

        val baseTotals = Totals(portfolio.base)
        baseTotals.marketValue = BigDecimal("10000.00")
        positions.setTotal(Position.In.BASE, baseTotals)

        // When
        val result = sectorExposureService.calculateSectorExposure(positions, Position.In.BASE)

        // Then
        assertThat(result.currency).isEqualTo(portfolio.base.code)
        assertThat(result.totalValue).isEqualByComparingTo(BigDecimal("10000.00"))
    }

    // ===== Helper Methods =====

    private fun createAssetWithCategory(
        code: String,
        category: String
    ): Asset {
        val asset = TestHelpers.createTestAsset(code, "US")
        asset.assetCategory = AssetCategory(id = category, name = category)
        return asset
    }

    private fun createPositionsWithAsset(
        asset: Asset,
        marketValue: BigDecimal
    ): Positions {
        val positions = Positions(portfolio)
        addPositionToPositions(positions, asset, marketValue)

        val totals = Totals(portfolio.currency)
        totals.marketValue = marketValue
        positions.setTotal(Position.In.PORTFOLIO, totals)

        return positions
    }

    private fun addPositionToPositions(
        positions: Positions,
        asset: Asset,
        marketValue: BigDecimal
    ) {
        val position = Position(asset, portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(portfolio.currency).apply {
                this.marketValue = marketValue
            }
        positions.add(position)
    }

    private fun createAssetExposure(
        asset: Asset,
        sectorName: String,
        weight: BigDecimal
    ): AssetExposure {
        val item =
            ClassificationItem(
                id = UUID.randomUUID().toString(),
                standard = testStandard,
                level = ClassificationLevel.SECTOR,
                code = sectorName.uppercase().replace(" ", "_"),
                name = sectorName
            )
        return AssetExposure(
            id = UUID.randomUUID().toString(),
            asset = asset,
            standard = testStandard,
            item = item,
            weight = weight,
            asOf = LocalDate.now()
        )
    }

    private fun createAssetClassification(
        asset: Asset,
        level: ClassificationLevel,
        itemName: String
    ): AssetClassification {
        val item =
            ClassificationItem(
                id = UUID.randomUUID().toString(),
                standard = testStandard,
                level = level,
                code = itemName.uppercase().replace(" ", "_"),
                name = itemName
            )
        return AssetClassification(
            id = UUID.randomUUID().toString(),
            asset = asset,
            standard = testStandard,
            item = item,
            level = level,
            source = AssetClassification.SOURCE_ALPHA_OVERVIEW,
            asOf = LocalDate.now()
        )
    }
}