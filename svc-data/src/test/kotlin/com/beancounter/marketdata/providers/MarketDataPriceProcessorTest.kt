package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for MarketDataPriceProcessor, specifically covering market closure scenarios.
 */
class MarketDataPriceProcessorTest {
    private lateinit var processor: MarketDataPriceProcessor
    private lateinit var providerUtils: ProviderUtils
    private lateinit var priceService: PriceService
    private lateinit var utilityService: MarketDataUtilityService
    private lateinit var mockProvider: MarketDataPriceProvider

    private val asset = Asset(code = "AAPL", id = "aapl-id", market = NASDAQ)
    private val priceAsset = PriceAsset(asset)

    @BeforeEach
    fun setUp() {
        providerUtils = mock(ProviderUtils::class.java)
        priceService = mock(PriceService::class.java)
        utilityService = mock(MarketDataUtilityService::class.java)
        mockProvider = mock(MarketDataPriceProvider::class.java)

        processor = MarketDataPriceProcessor(providerUtils, priceService, utilityService)
    }

    @Test
    fun `should return fallback price when market is closed and provider returns nothing`() {
        // Given: December 25 (Christmas) - market is closed
        val holidayDate = LocalDate.of(2024, 12, 25)
        val lastTradingDay = LocalDate.of(2024, 12, 24)

        val priceRequest =
            PriceRequest(
                date = holidayDate.toString(),
                assets = listOf(priceAsset),
                currentMode = true
            )

        // Last trading day has a price in DB
        val lastTradingDayPrice =
            MarketData(
                asset = asset,
                priceDate = lastTradingDay,
                close = BigDecimal("150.00")
            )

        // Setup mocks
        `when`(providerUtils.splitProviders(priceRequest.assets))
            .thenReturn(mutableMapOf(mockProvider to mutableListOf(asset)))

        `when`(mockProvider.getId()).thenReturn("ALPHA")
        `when`(mockProvider.isApiSupported()).thenReturn(true)

        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(holidayDate)

        // No price exists for Christmas day
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(emptyList())

        // Provider returns nothing (market closed)
        `when`(mockProvider.getMarketData(any()))
            .thenReturn(emptyList())

        // Fallback: most recent price before holiday
        `when`(priceService.getLatestMarketData(asset, holidayDate))
            .thenReturn(lastTradingDayPrice)

        `when`(providerUtils.getInputs(any<List<Asset>>()))
            .thenReturn(listOf(priceAsset))

        // When: Requesting prices for the closed market day
        val response = processor.getPriceResponse(priceRequest)

        // Then: Should return the fallback price from last trading day
        assertThat(response.data).hasSize(1)
        assertThat(response.data.first().close).isEqualByComparingTo(BigDecimal("150.00"))
    }

    @Test
    fun `should return provider price when market is open`() {
        // Given: A normal trading day
        val tradingDay = LocalDate.of(2024, 12, 23)

        val priceRequest =
            PriceRequest(
                date = tradingDay.toString(),
                assets = listOf(priceAsset),
                currentMode = true
            )

        val providerPrice =
            MarketData(
                asset = asset,
                priceDate = tradingDay,
                close = BigDecimal("155.00")
            )

        // Setup mocks
        `when`(providerUtils.splitProviders(priceRequest.assets))
            .thenReturn(mutableMapOf(mockProvider to mutableListOf(asset)))

        `when`(mockProvider.getId()).thenReturn("ALPHA")
        `when`(mockProvider.isApiSupported()).thenReturn(true)

        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(tradingDay)

        // No price in DB yet
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(emptyList())

        // Provider returns fresh price
        `when`(mockProvider.getMarketData(any()))
            .thenReturn(listOf(providerPrice))

        `when`(providerUtils.getInputs(any<List<Asset>>()))
            .thenReturn(listOf(priceAsset))

        // When: Requesting prices for an open market day
        val response = processor.getPriceResponse(priceRequest)

        // Then: Should return the provider price
        assertThat(response.data).hasSize(1)
        assertThat(response.data.first().close).isEqualByComparingTo(BigDecimal("155.00"))
    }

    @Test
    fun `should return fallback price when provider returns zero prices for closed market`() {
        // Given: December 25 (Christmas) - provider returns price with close=0
        val holidayDate = LocalDate.of(2024, 12, 25)
        val lastTradingDay = LocalDate.of(2024, 12, 24)

        val priceRequest =
            PriceRequest(
                date = holidayDate.toString(),
                assets = listOf(priceAsset),
                currentMode = true
            )

        // Provider returns invalid price (close=0) for holiday
        val invalidPrice =
            MarketData(
                asset = asset,
                priceDate = holidayDate,
                close = BigDecimal.ZERO
            )

        // Last trading day has a valid price in DB
        val lastTradingDayPrice =
            MarketData(
                asset = asset,
                priceDate = lastTradingDay,
                close = BigDecimal("150.00")
            )

        // Setup mocks
        `when`(providerUtils.splitProviders(priceRequest.assets))
            .thenReturn(mutableMapOf(mockProvider to mutableListOf(asset)))

        `when`(mockProvider.getId()).thenReturn("ALPHA")
        `when`(mockProvider.isApiSupported()).thenReturn(true)

        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(holidayDate)

        // No price exists for Christmas day in DB
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(emptyList())

        // Provider returns price with close=0 (invalid)
        `when`(mockProvider.getMarketData(any()))
            .thenReturn(listOf(invalidPrice))

        // Fallback: most recent valid price before holiday
        `when`(priceService.getLatestMarketData(asset, holidayDate))
            .thenReturn(lastTradingDayPrice)

        `when`(providerUtils.getInputs(any<List<Asset>>()))
            .thenReturn(listOf(priceAsset))

        // When: Requesting prices for the closed market day
        val response = processor.getPriceResponse(priceRequest)

        // Then: Should return the fallback price from last trading day, not the zero price
        assertThat(response.data).hasSize(1)
        assertThat(response.data.first().close).isEqualByComparingTo(BigDecimal("150.00"))
    }

    @Test
    fun `should return cached price from DB when available`() {
        // Given: A trading day with price already in DB
        val tradingDay = LocalDate.of(2024, 12, 23)

        val priceRequest =
            PriceRequest(
                date = tradingDay.toString(),
                assets = listOf(priceAsset),
                currentMode = true
            )

        val cachedPrice =
            MarketData(
                asset = asset,
                priceDate = tradingDay,
                close = BigDecimal("152.00")
            )

        // Setup mocks
        `when`(providerUtils.splitProviders(priceRequest.assets))
            .thenReturn(mutableMapOf(mockProvider to mutableListOf(asset)))

        `when`(mockProvider.getId()).thenReturn("ALPHA")

        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(tradingDay)

        // Price already exists in DB
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(listOf(cachedPrice))

        // When: Requesting prices
        val response = processor.getPriceResponse(priceRequest)

        // Then: Should return the cached price from DB
        assertThat(response.data).hasSize(1)
        assertThat(response.data.first().close).isEqualByComparingTo(BigDecimal("152.00"))
    }
}