package com.beancounter.position.mcp

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Totals
import com.beancounter.position.valuation.ValuationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@SpringBootTest(classes = [PositionMcpServer::class])
@ActiveProfiles("test")
class PositionMcpServerTest {
    @MockitoBean
    private lateinit var valuationService: ValuationService

    private lateinit var positionMcpServer: PositionMcpServer

    private val testCurrency = Currency(code = "USD", name = "US Dollar", symbol = "$")
    private val testMarket = Market(code = "NYSE", currencyId = "USD", timezoneId = "US/Eastern")
    private val testAsset =
        Asset(
            id = "test-asset-1",
            code = "AAPL",
            name = "Apple Inc.",
            market = testMarket,
            category = "EQUITY"
        )
    private val testPortfolio =
        Portfolio(
            id = "test-portfolio-1",
            code = "TEST",
            name = "Test Portfolio",
            currency = testCurrency,
            base = testCurrency,
            owner = SystemUser(id = "test-user", email = "test@example.com")
        )

    private val testPositions =
        Positions(testPortfolio).apply {
            val position = Position(testAsset)
            position.quantityValues.purchased = BigDecimal("100")
            positions[testAsset.id] = position

            // Add totals
            val baseTotals =
                Totals(testCurrency).apply {
                    marketValue = BigDecimal("15000.00")
                    purchases = BigDecimal("14000.00")
                    sales = BigDecimal.ZERO
                    gain = BigDecimal("1000.00")
                    income = BigDecimal("50.00")
                    irr = BigDecimal("0.12")
                }
            setTotal(Position.In.BASE, baseTotals)
            setTotal(Position.In.PORTFOLIO, baseTotals)
            setTotal(Position.In.TRADE, baseTotals)
        }

    @BeforeEach
    fun setUp() {
        positionMcpServer = PositionMcpServer(valuationService)
    }

    @Test
    fun `should get positions successfully`() {
        // Given
        val valuationDate = "2024-01-15"
        val includeValues = true
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, includeValues))
            .thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.getPositions(testPortfolio, valuationDate, includeValues)

        // Then
        assertEquals(expectedResponse, result)
        assertEquals(testPositions, result.data)
        verify(valuationService).getPositions(testPortfolio, valuationDate, includeValues)
    }

    @Test
    fun `should get positions with default parameters`() {
        // Given
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.getPositions(testPortfolio, "today", true))
            .thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.getPositions(testPortfolio)

        // Then
        assertEquals(expectedResponse, result)
        verify(valuationService).getPositions(testPortfolio, "today", true)
    }

    @Test
    fun `should query positions successfully`() {
        // Given
        val query =
            TrustedTrnQuery(
                portfolio = testPortfolio,
                assetId = "test-asset-1"
            )
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.build(query)).thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.queryPositions(query)

        // Then
        assertEquals(expectedResponse, result)
        verify(valuationService).build(query)
    }

    @Test
    fun `should build positions successfully`() {
        // Given
        val valuationDate = "2024-01-15"
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.build(testPortfolio, valuationDate)).thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.buildPositions(testPortfolio, valuationDate)

        // Then
        assertEquals(expectedResponse, result)
        verify(valuationService).build(testPortfolio, valuationDate)
    }

    @Test
    fun `should value positions successfully`() {
        // Given
        val positionResponse = PositionResponse(testPositions)
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.value(testPositions)).thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.valuePositions(positionResponse)

        // Then
        assertEquals(expectedResponse, result)
        verify(valuationService).value(testPositions)
    }

    @Test
    fun `should get portfolio metrics successfully`() {
        // Given
        val valuationDate = "2024-01-15"
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, true))
            .thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.getPortfolioMetrics(testPortfolio, valuationDate)

        // Then
        assertEquals(testPortfolio.id, result["portfolioId"])
        assertEquals(testPortfolio.code, result["portfolioCode"])
        assertEquals(testPortfolio.name, result["portfolioName"])
        assertEquals(testCurrency.code, result["currency"])
        assertEquals(valuationDate, result["asAt"])
        assertEquals(1, result["totalPositions"])
        assertEquals(true, result["hasPositions"])

        // Verify totals structure
        val totals = result["totals"] as Map<*, *>
        assertNotNull(totals["base"])
        assertNotNull(totals["portfolio"])
        assertNotNull(totals["trade"])

        val baseTotals = totals["base"] as Map<*, *>
        assertEquals(BigDecimal("15000.00"), baseTotals["marketValue"])
        assertEquals(BigDecimal("1000.00"), baseTotals["gain"])

        verify(valuationService).getPositions(testPortfolio, valuationDate, true)
    }

    @Test
    fun `should get portfolio metrics with default date`() {
        // Given
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.getPositions(testPortfolio, "today", true))
            .thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.getPortfolioMetrics(testPortfolio)

        // Then
        assertEquals("today", result["asAt"])
        verify(valuationService).getPositions(testPortfolio, "today", true)
    }

    @Test
    fun `should get position breakdown successfully`() {
        // Given
        val valuationDate = "2024-01-15"
        val expectedResponse = PositionResponse(testPositions)
        whenever(valuationService.getPositions(testPortfolio, valuationDate, true))
            .thenReturn(expectedResponse)

        // When
        val result = positionMcpServer.getPositionBreakdown(testPortfolio, valuationDate)

        // Then
        // Verify portfolio info
        val portfolioInfo = result["portfolio"] as Map<*, *>
        assertEquals(testPortfolio.id, portfolioInfo["id"])
        assertEquals(testPortfolio.code, portfolioInfo["code"])
        assertEquals(testPortfolio.name, portfolioInfo["name"])

        // Verify summary
        val summary = result["summary"] as Map<*, *>
        assertEquals(1, summary["totalPositions"])
        assertEquals(true, summary["hasPositions"])

        // Verify positions structure
        val positions = result["positions"] as List<*>
        assertEquals(1, positions.size)

        val position = positions[0] as Map<*, *>
        val asset = position["asset"] as Map<*, *>
        assertEquals(testAsset.id, asset["id"])
        assertEquals(testAsset.code, asset["code"])

        verify(valuationService).getPositions(testPortfolio, valuationDate, true)
    }

    @Test
    fun `should return available tools correctly`() {
        // When
        val tools = positionMcpServer.getAvailableTools()

        // Then
        assertEquals(6, tools.size)
        assertTrue(tools.containsKey("get_positions"))
        assertTrue(tools.containsKey("query_positions"))
        assertTrue(tools.containsKey("build_positions"))
        assertTrue(tools.containsKey("value_positions"))
        assertTrue(tools.containsKey("get_portfolio_metrics"))
        assertTrue(tools.containsKey("get_position_breakdown"))

        // Verify descriptions are meaningful
        assertTrue(tools["get_positions"]!!.contains("portfolio positions"))
        assertTrue(tools["get_portfolio_metrics"]!!.contains("performance metrics"))
        assertTrue(tools["value_positions"]!!.contains("market values"))
    }

    @Test
    fun `should handle exceptions gracefully when getting positions`() {
        // Given
        whenever(valuationService.getPositions(any(), any(), any()))
            .thenThrow(RuntimeException("Valuation failed"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            positionMcpServer.getPositions(testPortfolio, "2024-01-15", true)
        }
    }

    @Test
    fun `should handle exceptions gracefully when building positions`() {
        // Given
        whenever(valuationService.build(any<Portfolio>(), any<String>()))
            .thenThrow(RuntimeException("Build failed"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            positionMcpServer.buildPositions(testPortfolio, "2024-01-15")
        }
    }
}