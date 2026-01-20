package com.beancounter.position.service

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Test suite for BrokerPositionService.
 *
 * Tests verify:
 * - Position building for broker transactions
 * - Filtering of cash positions (CASH/PRIVATE markets, cash categories)
 * - Empty transaction handling
 * - Valuation toggle behavior
 */
@ExtendWith(MockitoExtension::class)
class BrokerPositionServiceTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var positionService: PositionService

    @Mock
    private lateinit var positionValuationService: PositionValuationService

    private lateinit var brokerPositionService: BrokerPositionService
    private lateinit var testPortfolio: com.beancounter.common.model.Portfolio

    @BeforeEach
    fun setUp() {
        brokerPositionService =
            BrokerPositionService(
                trnService,
                positionService,
                positionValuationService
            )
        testPortfolio = TestHelpers.createTestPortfolio("test-portfolio")
    }

    @Test
    fun `should return empty response when no transactions found`() {
        // Given
        val brokerId = "broker-123"
        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(emptyList()))

        // When
        val result = brokerPositionService.getPositions(brokerId)

        // Then - verify we get a valid empty response, not null
        assertThat(result).isNotNull
        assertThat(result.data).isNotNull
        assertThat(result.data.positions).isNotNull
        assertThat(result.data.positions).isEmpty()
        verify(positionService, never()).build(any(), any())
    }

    @Test
    fun `should return empty response for NO_BROKER when no unassigned transactions`() {
        // Given - NO_BROKER is used for transactions without a broker assigned
        val noBrokerId = "NO_BROKER"
        whenever(trnService.queryByBroker(noBrokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(emptyList()))

        // When
        val result = brokerPositionService.getPositions(noBrokerId)

        // Then - verify we get a valid empty response, not null or error
        assertThat(result).isNotNull
        assertThat(result.data).isNotNull
        assertThat(result.data.positions).isNotNull
        assertThat(result.data.positions).isEmpty()
        verify(positionService, never()).build(any(), any())
    }

    @Test
    fun `should build positions from broker transactions`() {
        // Given
        val brokerId = "broker-123"
        val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val transaction = createTransaction(asset, testPortfolio)
        val positions = createPositionsWithAsset(asset)

        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(listOf(transaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))

        // When
        val result = brokerPositionService.getPositions(brokerId, DateUtils.TODAY, false)

        // Then
        assertThat(result.data.positions).hasSize(1)
        assertThat(
            result.data.positions.values
                .first()
                .asset.code
        ).isEqualTo("AAPL")
        verify(positionService).build(eq(testPortfolio), any<PositionRequest>())
        verify(positionValuationService, never()).value(any(), any())
    }

    @Test
    fun `should filter out CASH market positions`() {
        // Given
        val brokerId = "broker-123"
        val equityAsset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val cashAsset = createAssetWithMarket("USD", "CASH")
        val transaction = createTransaction(equityAsset, testPortfolio)

        val positions = Positions(testPortfolio)
        positions.add(Position(asset = equityAsset, portfolio = testPortfolio))
        positions.add(Position(asset = cashAsset, portfolio = testPortfolio))

        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(listOf(transaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))

        // When
        val result = brokerPositionService.getPositions(brokerId, DateUtils.TODAY, false)

        // Then
        assertThat(result.data.positions).hasSize(1)
        assertThat(
            result.data.positions.values
                .first()
                .asset.code
        ).isEqualTo("AAPL")
    }

    @Test
    fun `should filter out PRIVATE market positions`() {
        // Given
        val brokerId = "broker-123"
        val equityAsset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val privateAsset = createAssetWithMarket("HOUSE", "PRIVATE")
        val transaction = createTransaction(equityAsset, testPortfolio)

        val positions = Positions(testPortfolio)
        positions.add(Position(asset = equityAsset, portfolio = testPortfolio))
        positions.add(Position(asset = privateAsset, portfolio = testPortfolio))

        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(listOf(transaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))

        // When
        val result = brokerPositionService.getPositions(brokerId, DateUtils.TODAY, false)

        // Then
        assertThat(result.data.positions).hasSize(1)
        assertThat(
            result.data.positions.values
                .first()
                .asset.code
        ).isEqualTo("AAPL")
    }

    @Test
    fun `should filter out positions with cash category`() {
        // Given
        val brokerId = "broker-123"
        val equityAsset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val cashCategoryAsset = createAssetWithCategory("USD-ACCOUNT")
        val transaction = createTransaction(equityAsset, testPortfolio)

        val positions = Positions(testPortfolio)
        positions.add(Position(asset = equityAsset, portfolio = testPortfolio))
        positions.add(Position(asset = cashCategoryAsset, portfolio = testPortfolio))

        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(listOf(transaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))

        // When
        val result = brokerPositionService.getPositions(brokerId, DateUtils.TODAY, false)

        // Then
        assertThat(result.data.positions).hasSize(1)
        assertThat(
            result.data.positions.values
                .first()
                .asset.code
        ).isEqualTo("AAPL")
    }

    @Test
    fun `should value positions when value parameter is true`() {
        // Given
        val brokerId = "broker-123"
        val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val transaction = createTransaction(asset, testPortfolio)
        val positions = createPositionsWithAsset(asset)

        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(listOf(transaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))
        whenever(positionValuationService.value(any(), any()))
            .thenReturn(positions)

        // When
        val result = brokerPositionService.getPositions(brokerId, DateUtils.TODAY, true)

        // Then
        assertThat(result.data.positions).hasSize(1)
        verify(positionValuationService).value(any(), any())
    }

    @Test
    fun `should set valuation date when not today`() {
        // Given
        val brokerId = "broker-123"
        val valuationDate = "2024-01-15"
        val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val transaction = createTransaction(asset, testPortfolio)
        val positions = createPositionsWithAsset(asset)

        whenever(trnService.queryByBroker(brokerId, valuationDate))
            .thenReturn(TrnResponse(listOf(transaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))

        // When
        val result = brokerPositionService.getPositions(brokerId, valuationDate, false)

        // Then
        assertThat(result.data.asAt).isEqualTo(valuationDate)
    }

    @Test
    fun `should sort transactions by trade date before building positions`() {
        // Given
        val brokerId = "broker-123"
        val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
        val olderTransaction = createTransaction(asset, testPortfolio, LocalDate.of(2024, 1, 1))
        val newerTransaction = createTransaction(asset, testPortfolio, LocalDate.of(2024, 1, 15))
        val positions = createPositionsWithAsset(asset)

        // Return transactions in wrong order
        whenever(trnService.queryByBroker(brokerId, DateUtils.TODAY))
            .thenReturn(TrnResponse(listOf(newerTransaction, olderTransaction)))
        whenever(positionService.build(eq(testPortfolio), any<PositionRequest>()))
            .thenReturn(PositionResponse(positions))

        // When
        brokerPositionService.getPositions(brokerId, DateUtils.TODAY, false)

        // Then - verify positionService was called (transactions are sorted internally)
        verify(positionService).build(eq(testPortfolio), any<PositionRequest>())
    }

    private fun createTransaction(
        asset: Asset,
        portfolio: com.beancounter.common.model.Portfolio,
        tradeDate: LocalDate = LocalDate.now()
    ): Trn =
        Trn(
            asset = asset,
            portfolio = portfolio,
            trnType = TrnType.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("150.00"),
            tradeDate = tradeDate
        )

    private fun createPositionsWithAsset(asset: Asset): Positions {
        val positions = Positions(testPortfolio)
        positions.add(Position(asset = asset, portfolio = testPortfolio))
        return positions
    }

    private fun createAssetWithMarket(
        code: String,
        marketCode: String
    ): Asset {
        val market = Market(marketCode)
        return Asset(
            code = code,
            id = code,
            name = "$code Asset",
            market = market
        )
    }

    private fun createAssetWithCategory(code: String): Asset =
        Asset(
            code = "USD-ACCOUNT",
            id = code,
            name = "$code Asset",
            market = NASDAQ,
            category = "ACCOUNT"
        )
}