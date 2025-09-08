package com.beancounter.marketdata.mcp

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.trn.TrnService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@SpringBootTest(classes = [DataMcpServer::class])
@ActiveProfiles("test")
class DataMcpServerTest {
    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var portfolioService: PortfolioService

    @MockitoBean
    private lateinit var priceService: PriceService

    @MockitoBean
    private lateinit var trnService: TrnService

    @MockitoBean
    private lateinit var fxRateService: FxRateService

    @MockitoBean
    private lateinit var marketService: MarketService

    @MockitoBean
    private lateinit var currencyService: CurrencyService

    private lateinit var dataMcpServer: DataMcpServer

    private val testMarket = Market(code = "NYSE", currencyId = "USD", timezoneId = "US/Eastern")
    private val testCurrency =
        com.beancounter.common.model
            .Currency(code = "USD")
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

    @BeforeEach
    fun setUp() {
        dataMcpServer =
            DataMcpServer(
                assetService,
                portfolioService,
                priceService,
                trnService,
                fxRateService,
                marketService,
                currencyService,
                DateUtils()
            )
    }

    @Test
    fun `should find or create asset successfully`() {
        // Given
        val market = "NYSE"
        val code = "AAPL"
        val category = "EQUITY"
        whenever(assetService.findOrCreate(any<AssetInput>())).thenReturn(testAsset)

        // When
        val result = dataMcpServer.findOrCreateAsset(market, code, category)

        // Then
        assertEquals(testAsset, result)
        verify(assetService).findOrCreate(any<AssetInput>())
    }

    @Test
    fun `should get asset by ID successfully`() {
        // Given
        whenever(assetService.find("test-asset-1")).thenReturn(testAsset)

        // When
        val result = dataMcpServer.getAsset("test-asset-1")

        // Then
        assertEquals(testAsset, result)
        verify(assetService).find("test-asset-1")
    }

    @Test
    fun `should get portfolios successfully`() {
        // Given
        val portfolios = listOf(testPortfolio)
        val expectedResponse = PortfoliosResponse(portfolios)
        whenever(portfolioService.portfolios()).thenReturn(portfolios)

        // When
        val result = dataMcpServer.getPortfolios()

        // Then
        assertEquals(expectedResponse.data, result.data)
        verify(portfolioService).portfolios()
    }

    @Test
    fun `should get portfolio by ID successfully`() {
        // Given
        whenever(portfolioService.find("test-portfolio-1")).thenReturn(testPortfolio)

        // When
        val result = dataMcpServer.getPortfolio("test-portfolio-1")

        // Then
        assertEquals(testPortfolio, result)
        verify(portfolioService).find("test-portfolio-1")
    }

    @Test
    fun `should get portfolio by code successfully`() {
        // Given
        whenever(portfolioService.findByCode("TEST")).thenReturn(testPortfolio)

        // When
        val result = dataMcpServer.getPortfolioByCode("TEST")

        // Then
        assertEquals(testPortfolio, result)
        verify(portfolioService).findByCode("TEST")
    }

    @Test
    fun `should get portfolios where held successfully`() {
        // Given
        val assetId = "test-asset-1"
        val tradeDate = "2024-01-15"
        val expectedResponse = PortfoliosResponse(listOf(testPortfolio))
        whenever(portfolioService.findWhereHeld(assetId, LocalDate.of(2024, 1, 15)))
            .thenReturn(expectedResponse)

        // When
        val result = dataMcpServer.getPortfoliosWhereHeld(assetId, tradeDate)

        // Then
        assertEquals(expectedResponse, result)
        verify(portfolioService).findWhereHeld(assetId, LocalDate.of(2024, 1, 15))
    }

    @Test
    fun `should get portfolios where held with null date successfully`() {
        // Given
        val assetId = "test-asset-1"
        val expectedResponse = PortfoliosResponse(listOf(testPortfolio))
        whenever(portfolioService.findWhereHeld(assetId, null)).thenReturn(expectedResponse)

        // When
        val result = dataMcpServer.getPortfoliosWhereHeld(assetId)

        // Then
        assertEquals(expectedResponse, result)
        verify(portfolioService).findWhereHeld(assetId, null)
    }

    @Test
    fun `should get transactions for portfolio successfully`() {
        // Given
        val portfolioId = "test-portfolio-1"
        val tradeDate = "2024-01-15"
        val transactions = listOf<Trn>() // Empty list for simplicity
        whenever(trnService.findForPortfolio(portfolioId, LocalDate.of(2024, 1, 15)))
            .thenReturn(transactions)

        // When
        val result = dataMcpServer.getTransactionsForPortfolio(portfolioId, tradeDate)

        // Then
        assertEquals(transactions, result)
        verify(trnService).findForPortfolio(portfolioId, LocalDate.of(2024, 1, 15))
    }

    @Test
    fun `should get market data successfully when data exists`() {
        // Given
        val assetId = "test-asset-1"
        val date = "2024-01-15"
        val marketData =
            MarketData(
                asset = testAsset,
                source = "TEST",
                priceDate = LocalDate.of(2024, 1, 15),
                open = BigDecimal("148.00"),
                close = BigDecimal("150.00"),
                low = BigDecimal("147.00"),
                high = BigDecimal("152.00"),
                dividend = BigDecimal("0.50")
            )
        whenever(priceService.getMarketData(assetId, LocalDate.of(2024, 1, 15)))
            .thenReturn(Optional.of(marketData))

        // When
        val result = dataMcpServer.getMarketData(assetId, date)

        // Then
        assertEquals(assetId, result["assetId"])
        assertEquals(date, result["date"])
        assertEquals(BigDecimal("150.00"), result["close"])
        assertEquals(BigDecimal("148.00"), result["open"])
        assertEquals(BigDecimal("152.00"), result["high"])
        assertEquals(BigDecimal("147.00"), result["low"])
        assertEquals(BigDecimal("0.50"), result["dividend"])
        verify(priceService).getMarketData(assetId, LocalDate.of(2024, 1, 15))
    }

    @Test
    fun `should handle missing market data gracefully`() {
        // Given
        val assetId = "test-asset-1"
        val date = "2024-01-15"
        whenever(priceService.getMarketData(assetId, LocalDate.of(2024, 1, 15)))
            .thenReturn(Optional.empty())

        // When
        val result = dataMcpServer.getMarketData(assetId, date)

        // Then
        assertEquals(assetId, result["assetId"])
        assertEquals(date, result["date"])
        assertTrue(result["error"].toString().contains("No market data found"))
        verify(priceService).getMarketData(assetId, LocalDate.of(2024, 1, 15))
    }

    @Test
    fun `should get FX rates successfully`() {
        // Given
        val fromCurrency = "USD"
        val toCurrency = "EUR"
        val rateDate = "2024-01-15"
        val expectedResponse = FxResponse()
        whenever(fxRateService.getRates(any<FxRequest>(), any())).thenReturn(expectedResponse)

        // When
        val result = dataMcpServer.getFxRates(fromCurrency, toCurrency, rateDate)

        // Then
        assertEquals(expectedResponse, result)
        verify(fxRateService).getRates(any<FxRequest>(), any())
    }

    @Test
    fun `should get FX rates with default date when not provided`() {
        // Given
        val fromCurrency = "USD"
        val toCurrency = "EUR"
        val expectedResponse = FxResponse()
        whenever(fxRateService.getRates(any<FxRequest>(), any())).thenReturn(expectedResponse)

        // When
        val result = dataMcpServer.getFxRates(fromCurrency, toCurrency)

        // Then
        assertEquals(expectedResponse, result)
        verify(fxRateService).getRates(any<FxRequest>(), any())
    }

    @Test
    fun `should get markets successfully`() {
        // Given
        val expectedResponse = MarketResponse(listOf(testMarket))
        whenever(marketService.getMarkets()).thenReturn(expectedResponse)

        // When
        val result = dataMcpServer.getMarkets()

        // Then
        assertEquals(expectedResponse, result)
        verify(marketService).getMarkets()
    }

    @Test
    fun `should get currencies successfully`() {
        // Given
        val currencies = listOf(testCurrency)
        whenever(currencyService.currencies()).thenReturn(currencies)

        // When
        val result = dataMcpServer.getCurrencies()

        // Then
        assertEquals(currencies, result)
        verify(currencyService).currencies()
    }

    @Test
    fun `should get current prices successfully`() {
        // Given
        val assetIds = "asset-1,asset-2"
        val date = "2024-01-15"
        val marketDataList =
            listOf(
                MarketData(
                    asset = testAsset,
                    priceDate = LocalDate.of(2024, 1, 15),
                    close = BigDecimal("150.00")
                )
            )
        whenever(assetService.find("asset-1")).thenReturn(testAsset)
        whenever(assetService.find("asset-2")).thenReturn(testAsset)
        whenever(priceService.getMarketData(any<Collection<Asset>>(), any<LocalDate>()))
            .thenReturn(marketDataList)

        // When
        val result = dataMcpServer.getCurrentPrices(assetIds, date)

        // Then
        assertEquals("2024-01-15", result["date"])
        assertNotNull(result["prices"])
        val prices = result["prices"] as List<*>
        assertTrue(prices.isNotEmpty())
        verify(priceService).getMarketData(any<Collection<Asset>>(), any<LocalDate>())
    }

    @Test
    fun `should return available tools correctly`() {
        // When
        val tools = dataMcpServer.getAvailableTools()

        // Then
        assertFalse(tools.isEmpty())
    }

    @Test
    fun `should handle exceptions gracefully when finding asset`() {
        // Given
        whenever(assetService.find("invalid-asset")).thenThrow(RuntimeException("Asset not found"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            dataMcpServer.getAsset("invalid-asset")
        }
    }

    @Test
    fun `should handle exceptions gracefully when getting portfolio`() {
        // Given
        whenever(portfolioService.find("invalid-portfolio")).thenThrow(RuntimeException("Portfolio not found"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            dataMcpServer.getPortfolio("invalid-portfolio")
        }
    }

    @Test
    fun `should parse date correctly in getMarketData`() {
        // Given
        val assetId = "test-asset-1"
        val date = "2024-01-15"
        val expectedDate = LocalDate.of(2024, 1, 15)
        whenever(priceService.getMarketData(assetId, expectedDate)).thenReturn(Optional.empty())

        // When
        val result = dataMcpServer.getMarketData(assetId, date)

        // Then
        assertEquals(assetId, result["assetId"])
        assertEquals(date, result["date"])
        verify(priceService).getMarketData(assetId, expectedDate)
    }

    @Test
    fun `should handle current date in getTransactionsForPortfolio when date is null`() {
        // Given
        val portfolioId = "test-portfolio-1"
        val today = LocalDate.now()
        val transactions = listOf<Trn>()
        whenever(trnService.findForPortfolio(portfolioId, today)).thenReturn(transactions)

        // When
        val result = dataMcpServer.getTransactionsForPortfolio(portfolioId)

        // Then
        assertEquals(transactions, result)
        verify(trnService).findForPortfolio(portfolioId, today)
    }
}