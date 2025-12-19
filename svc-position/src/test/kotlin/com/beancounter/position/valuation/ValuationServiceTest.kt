package com.beancounter.position.valuation

import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

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

    private lateinit var valuationService: ValuationService

    private val portfolio = TestHelpers.createTestPortfolio("ValuationServiceTest")

    @BeforeEach
    fun setup() {
        valuationService =
            ValuationService(
                positionValuationService,
                trnService,
                positionService,
                marketValueUpdateProducer
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
            com.beancounter.common.model
                .Totals(portfolio.currency)
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
}