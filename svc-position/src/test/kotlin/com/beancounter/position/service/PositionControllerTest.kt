package com.beancounter.position.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.utils.TestHelpers
import com.beancounter.position.valuation.Valuation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Test suite for PositionController to ensure proper position retrieval functionality.
 *
 * This class tests:
 * - Position retrieval by portfolio ID
 * - Position retrieval by portfolio code
 * - Position query functionality
 * - Request parameter handling
 * - Response formatting and validation
 *
 * Tests verify that the PositionController correctly handles
 * various position retrieval requests and returns appropriate responses.
 */
@ExtendWith(MockitoExtension::class)
class PositionControllerTest {
    @Mock
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Mock
    private lateinit var dateUtils: DateUtils

    @Mock
    private lateinit var valuationService: Valuation

    private lateinit var positionController: PositionController

    private lateinit var testPortfolio: Portfolio
    private lateinit var testPositionResponse: PositionResponse

    @BeforeEach
    fun setUp() {
        testPortfolio = TestHelpers.createTestPortfolio("test-portfolio")
        testPositionResponse = PositionResponse(TestHelpers.createTestPositions(testPortfolio))

        positionController = PositionController(portfolioServiceClient, dateUtils)
        positionController.setValuationService(valuationService)
    }

    @Test
    fun `should return positions when retrieving by portfolio ID`() {
        // Given
        val portfolioId = "test-portfolio"
        val valuationDate = "2024-01-15"
        val value = true

        whenever(portfolioServiceClient.getPortfolioById(portfolioId)).thenReturn(testPortfolio)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, value))
            .thenReturn(testPositionResponse)

        // When
        val result = positionController.byId(portfolioId, valuationDate, value)

        // Then
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(testPositionResponse)
        verify(portfolioServiceClient).getPortfolioById(portfolioId)
        verify(valuationService).getPositions(testPortfolio, valuationDate, value)
    }

    @Test
    fun `should return positions when retrieving by portfolio code`() {
        // Given
        val portfolioCode = "TEST"
        val valuationDate = "2024-01-15"
        val value = true

        whenever(portfolioServiceClient.getPortfolioByCode(portfolioCode)).thenReturn(testPortfolio)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, value))
            .thenReturn(testPositionResponse)

        // When
        val result = positionController.get(portfolioCode, valuationDate, value)

        // Then
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(testPositionResponse)
        verify(portfolioServiceClient).getPortfolioByCode(portfolioCode)
        verify(valuationService).getPositions(testPortfolio, valuationDate, value)
    }

    @Test
    fun `should return positions when querying with transaction query`() {
        // Given
        val trnQuery =
            TrustedTrnQuery(
                portfolio = testPortfolio,
                assetId = "AAPL"
            )

        whenever(valuationService.build(trnQuery)).thenReturn(testPositionResponse)

        // When
        val result = positionController.query(trnQuery)

        // Then
        assertThat(result).isNotNull()
        assertThat(result).isEqualTo(testPositionResponse)
        verify(valuationService).build(trnQuery)
    }

    @Test
    fun `should handle different valuation dates for portfolio ID`() {
        // Given
        val portfolioId = "test-portfolio"
        val valuationDate = "2024-01-15"

        whenever(portfolioServiceClient.getPortfolioById(portfolioId)).thenReturn(testPortfolio)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, true))
            .thenReturn(testPositionResponse)

        // When
        val result = positionController.byId(portfolioId, valuationDate, true)

        // Then
        assertThat(result).isNotNull()
        verify(valuationService).getPositions(testPortfolio, valuationDate, true)
    }

    @Test
    fun `should handle different valuation dates for portfolio code`() {
        // Given
        val portfolioCode = "TEST"
        val valuationDate = "2024-01-15"

        whenever(portfolioServiceClient.getPortfolioByCode(portfolioCode)).thenReturn(testPortfolio)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, true))
            .thenReturn(testPositionResponse)

        // When
        val result = positionController.get(portfolioCode, valuationDate, true)

        // Then
        assertThat(result).isNotNull()
        verify(valuationService).getPositions(testPortfolio, valuationDate, true)
    }

    @Test
    fun `should handle value parameter correctly when set to false`() {
        // Given
        val portfolioId = "test-portfolio"
        val valuationDate = "2024-01-15"
        val value = false

        whenever(portfolioServiceClient.getPortfolioById(portfolioId)).thenReturn(testPortfolio)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, value))
            .thenReturn(testPositionResponse)

        // When
        val result = positionController.byId(portfolioId, valuationDate, value)

        // Then
        assertThat(result).isNotNull()
        verify(valuationService).getPositions(testPortfolio, valuationDate, false)
    }
}