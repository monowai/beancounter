package com.beancounter.position.valuation

import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.service.MarketValueUpdateProducer
import com.beancounter.position.service.PositionService
import com.beancounter.position.service.PositionValuationService
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for ValuationService focusing on edge cases like fully sold portfolios.
 */
@ExtendWith(MockitoExtension::class)
class ValuationServiceTest {
    @Mock
    private lateinit var positionValuationService: PositionValuationService

    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var positionService: PositionService

    @Mock
    private lateinit var marketValueUpdateProducer: MarketValueUpdateProducer

    @Mock
    private lateinit var classificationClient: ClassificationClient

    private lateinit var valuationService: ValuationService

    private val portfolio = TestHelpers.createTestPortfolio("ValuationServiceTest")

    @BeforeEach
    fun setup() {
        // Default mock for classificationClient - returns empty response (lenient because not all tests use it)
        Mockito
            .lenient()
            .`when`(
                classificationClient.getClassifications(any())
            ).thenReturn(BulkClassificationResponse())

        valuationService =
            ValuationService(
                positionValuationService,
                trnService,
                positionService,
                marketValueUpdateProducer,
                classificationClient
            )
        // Set kafkaEnabled property that would normally be injected by Spring
        ReflectionTestUtils.setField(valuationService, "kafkaEnabled", "false")
    }

    @Test
    fun `value should send zero market value when all positions have zero quantity`() {
        // Given - a portfolio where all positions are sold (zero quantity)
        // This simulates the USX scenario where prices aren't fetched for zero-qty positions
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        // Add a position with zero quantity (fully sold)
        val asset = TestHelpers.createTestAsset("SOLD_ASSET", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.ZERO
        position.quantityValues.sold = BigDecimal.ZERO
        positions.add(position)

        // When positionValuationService.value() returns positions WITHOUT totals set
        // (this happens when no prices are fetched for zero-qty positions)
        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - should return successfully and send market value update with 0
        assertThat(result).isNotNull
        assertThat(result.data).isEqualTo(positions)
        // Market value update SHOULD be sent with 0 when totals are null (fully sold portfolio)
        verify(marketValueUpdateProducer).sendMessage(any())
    }

    @Test
    fun `value should send market value update when totals are set`() {
        // Given - a portfolio with valued positions that have totals
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        // Add a position with quantity
        val asset = TestHelpers.createTestAsset("HELD_ASSET", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        // Set up totals (what happens after successful valuation)
        val portfolioTotals =
            Totals(portfolio.currency)
        portfolioTotals.marketValue = BigDecimal("10000.00")
        portfolioTotals.irr = BigDecimal("0.15")
        positions.setTotal(Position.In.PORTFOLIO, portfolioTotals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - should return successfully and send market value update
        assertThat(result).isNotNull
        assertThat(result.data).isEqualTo(positions)
        verify(marketValueUpdateProducer).sendMessage(any())
    }

    @Test
    fun `getPositions should handle fully sold portfolio gracefully`() {
        // Given - a portfolio with no active positions
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY
        val positionResponse = PositionResponse(positions)

        whenever(trnService.query(any<com.beancounter.common.model.Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(emptyList()))
        whenever(positionService.build(any(), any())).thenReturn(positionResponse)

        // When - request valuation without market value (value=false to avoid needing positionValuationService mock)
        val result = valuationService.getPositions(portfolio, DateUtils.TODAY, value = false)

        // Then - should return empty positions without error
        assertThat(result).isNotNull
        assertThat(result.data.positions).isEmpty()
    }

    @Test
    fun `getAggregatedPositions returns empty response for empty portfolio list`() {
        // Given - empty portfolio list
        val portfolios = emptyList<com.beancounter.common.model.Portfolio>()

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then
        assertThat(result.data.positions).isEmpty()
    }

    @Test
    fun `getAggregatedPositions returns empty response when all portfolios have no transactions`() {
        // Given - portfolios with no transactions
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        whenever(trnService.query(any<com.beancounter.common.model.Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(emptyList()))

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then
        assertThat(result.data.positions).isEmpty()
    }

    @Test
    fun `getAggregatedPositions combines transactions from multiple portfolios`() {
        // Given - two portfolios with transactions
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset1 = TestHelpers.createTestAsset("AAPL", "US")
        val asset2 = TestHelpers.createTestAsset("GOOGL", "US")
        val trn1 =
            TestHelpers.createTestTransaction(
                asset = asset1,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = BigDecimal("150.00"),
                tradeDate = LocalDate.of(2024, 1, 15)
            )
        val trn2 =
            TestHelpers.createTestTransaction(
                asset = asset2,
                portfolio = portfolio2,
                trnType = TrnType.BUY,
                quantity = BigDecimal("5"),
                price = BigDecimal("2500.00"),
                tradeDate = LocalDate.of(2024, 1, 16)
            )

        whenever(trnService.query(argThat { id == portfolio.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn1)))
        whenever(trnService.query(argThat { id == portfolio2.id }, any<String>()))
            .thenReturn(TrnResponse(listOf(trn2)))

        val positions = Positions(portfolio)
        positions.add(Position(asset1, portfolio))
        positions.add(Position(asset2, portfolio))
        val positionResponse = PositionResponse(positions)

        whenever(positionService.build(any(), any())).thenReturn(positionResponse)

        // When
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = false)

        // Then
        assertThat(result.data.positions).hasSize(2)
    }

    @Test
    fun `getAggregatedPositions skips market value update for aggregated positions`() {
        // Given - aggregated positions that would normally trigger market value update
        val portfolio2 = TestHelpers.createTestPortfolio("Portfolio2")
        val portfolios = listOf(portfolio, portfolio2)

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trn =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                trnType = TrnType.BUY,
                quantity = BigDecimal("10"),
                price = BigDecimal("150.00"),
                tradeDate = LocalDate.of(2024, 1, 15)
            )

        whenever(trnService.query(any<com.beancounter.common.model.Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(listOf(trn)))

        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY
        val position = Position(asset, portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(portfolio.currency).apply {
                marketValue = BigDecimal("1500.00")
            }
        positions.add(position)
        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        whenever(positionService.build(any(), any())).thenReturn(PositionResponse(positions))
        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When - request valuation with value=true
        val result = valuationService.getAggregatedPositions(portfolios, DateUtils.TODAY, value = true)

        // Then - market value update should NOT be sent for aggregated positions
        verify(marketValueUpdateProducer, never()).sendMessage(any())
        assertThat(result.data.positions).isNotEmpty
    }

    @Test
    fun `value should not send market value update for historical dates`() {
        // Given - a portfolio valued at a historical date
        val positions = Positions(portfolio)
        positions.asAt = "2024-01-15" // Historical date

        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        val totals = Totals(portfolio.currency)
        totals.marketValue = BigDecimal("1500.00")
        positions.setTotal(Position.In.PORTFOLIO, totals)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - should NOT send market value update for historical dates
        verify(marketValueUpdateProducer, never()).sendMessage(any())
        assertThat(result.data).isEqualTo(positions)
    }

    @Test
    fun `enrichWithClassifications sets Cash sector for cash assets`() {
        // Given - positions with a cash asset
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        val cashAsset = TestHelpers.createTestAsset("USD", "OFFM")
        cashAsset.assetCategory = AssetCategory(id = AssetCategory.REPORT_CASH, name = "Cash")
        val position = Position(cashAsset, portfolio)
        position.quantityValues.purchased = BigDecimal("1000")
        positions.add(position)

        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - cash asset should have sector set to "Cash"
        val cashPosition =
            result.data.positions.values
                .first()
        assertThat(cashPosition.asset.sector).isEqualTo(AssetCategory.REPORT_CASH)
    }

    @Test
    fun `enrichWithClassifications applies sector and industry from classification service`() {
        // Given - positions with an equity asset
        val positions = Positions(portfolio)
        positions.asAt = DateUtils.TODAY

        val equityAsset = TestHelpers.createTestAsset("AAPL", "US")
        equityAsset.assetCategory = AssetCategory(id = AssetCategory.REPORT_EQUITY, name = "Equity")
        val position = Position(equityAsset, portfolio)
        position.quantityValues.purchased = BigDecimal.TEN
        positions.add(position)

        // Mock classification response
        val classifications =
            mapOf(
                equityAsset.id to
                    AssetClassificationSummary(
                        assetId = equityAsset.id,
                        sector = "Information Technology",
                        industry = "Consumer Electronics"
                    )
            )
        whenever(classificationClient.getClassifications(any()))
            .thenReturn(BulkClassificationResponse(classifications))
        whenever(positionValuationService.value(any(), any())).thenReturn(positions)

        // When
        val result = valuationService.value(positions)

        // Then - equity should have sector and industry set from classifications
        val equityPosition =
            result.data.positions.values
                .first()
        assertThat(equityPosition.asset.sector).isEqualTo("Information Technology")
        assertThat(equityPosition.asset.industry).isEqualTo("Consumer Electronics")
    }

    @Test
    fun `build with TrustedTrnQuery calls trnService with correct query`() {
        // Given - a trusted transaction query
        val tradeDate = LocalDate.of(2024, 1, 15)
        val asset = TestHelpers.createTestAsset("AAPL", "US")
        val trnQuery =
            TrustedTrnQuery(
                portfolio = portfolio,
                tradeDate = tradeDate,
                assetId = asset.id
            )

        val trn =
            TestHelpers.createTestTransaction(
                asset = asset,
                portfolio = portfolio,
                tradeDate = tradeDate
            )

        whenever(trnService.query(any<TrustedTrnQuery>())).thenReturn(TrnResponse(listOf(trn)))

        val positions = Positions(portfolio)
        positions.add(Position(asset, portfolio))
        whenever(positionService.build(any(), any())).thenReturn(PositionResponse(positions))

        // When
        val result = valuationService.build(trnQuery)

        // Then
        assertThat(result.data.positions).hasSize(1)
        verify(trnService).query(trnQuery)
    }

    @Test
    fun `getPositions sets asAt date for historical valuations`() {
        // Given
        val historicalDate = "2024-01-15"
        val positions = Positions(portfolio)
        val positionResponse = PositionResponse(positions)

        whenever(trnService.query(any<com.beancounter.common.model.Portfolio>(), any<String>()))
            .thenReturn(TrnResponse(emptyList()))
        whenever(positionService.build(any(), any())).thenReturn(positionResponse)

        // When
        val result = valuationService.getPositions(portfolio, historicalDate, value = false)

        // Then - asAt should be set to the historical date
        assertThat(result.data.asAt).isEqualTo(historicalDate)
    }

    @Test
    fun `value returns positions unchanged when no positions exist`() {
        // Given - empty positions
        val positions = Positions(portfolio)

        // When
        val result = valuationService.value(positions)

        // Then - should return the same positions without calling valuation service
        assertThat(result.data).isEqualTo(positions)
        verify(positionValuationService, never()).value(any(), any())
    }
}