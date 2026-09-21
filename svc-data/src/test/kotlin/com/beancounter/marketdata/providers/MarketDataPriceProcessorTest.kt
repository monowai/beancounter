package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
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

        `when`(mockProvider.getId()).thenReturn(AlphaPriceService.ID)
        `when`(mockProvider.isApiSupported()).thenReturn(true)

        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(holidayDate)

        // No price exists for Christmas day
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(emptyList())

        // Provider returns nothing (market closed)
        `when`(mockProvider.getMarketData(any()))
            .thenReturn(emptyList())

        // Fallback: most recent price before holiday, resolved for all remaining
        // assets in a single batched call
        `when`(priceService.getLatestMarketData(listOf(asset), holidayDate))
            .thenReturn(mapOf(asset.id to lastTradingDayPrice))

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

        `when`(mockProvider.getId()).thenReturn(AlphaPriceService.ID)
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

        `when`(mockProvider.getId()).thenReturn(AlphaPriceService.ID)
        `when`(mockProvider.isApiSupported()).thenReturn(true)

        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(holidayDate)

        // No price exists for Christmas day in DB
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(emptyList())

        // Provider returns price with close=0 (invalid)
        `when`(mockProvider.getMarketData(any()))
            .thenReturn(listOf(invalidPrice))

        // Fallback: most recent valid price before holiday, resolved for all remaining
        // assets in a single batched call
        `when`(priceService.getLatestMarketData(listOf(asset), holidayDate))
            .thenReturn(mapOf(asset.id to lastTradingDayPrice))

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

        `when`(mockProvider.getId()).thenReturn(AlphaPriceService.ID)

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

    @Test
    fun `should resolve holiday fallback for multiple assets in a single batched call (DATA-6G)`() {
        // Given: three assets, all falling back on a holiday - the N+1 Sentry flagged
        // (issue DATA-6G) was one getLatestMarketData call per remaining asset here.
        val holidayDate = LocalDate.of(2024, 12, 25)
        val lastTradingDay = LocalDate.of(2024, 12, 24)

        val assetB = Asset(code = "MSFT", id = "msft-id", market = NASDAQ)
        val assetC = Asset(code = "GOOG", id = "goog-id", market = NASDAQ)
        val remainingAssets = listOf(asset, assetB, assetC)
        val priceAssets = remainingAssets.map { PriceAsset(it) }

        val priceRequest =
            PriceRequest(
                date = holidayDate.toString(),
                assets = priceAssets,
                currentMode = true
            )

        val fallbackByAssetId =
            remainingAssets.associate { a ->
                a.id to MarketData(asset = a, priceDate = lastTradingDay, close = BigDecimal("100.00"))
            }

        `when`(providerUtils.splitProviders(priceRequest.assets))
            .thenReturn(mutableMapOf(mockProvider to remainingAssets.toMutableList()))
        `when`(mockProvider.getId()).thenReturn(AlphaPriceService.ID)
        `when`(mockProvider.isApiSupported()).thenReturn(true)
        `when`(utilityService.getMarketDate(mockProvider, asset, priceRequest))
            .thenReturn(holidayDate)
        `when`(priceService.getMarketData(any<Collection<Asset>>(), any()))
            .thenReturn(emptyList())
        `when`(mockProvider.getMarketData(any())).thenReturn(emptyList())
        `when`(providerUtils.getInputs(any<List<Asset>>())).thenReturn(priceAssets)

        // Fallback resolved for the whole remaining set in one call
        `when`(priceService.getLatestMarketData(remainingAssets, holidayDate))
            .thenReturn(fallbackByAssetId)

        // When: Requesting prices for the closed market day
        val response = processor.getPriceResponse(priceRequest)

        // Then: All three assets get their fallback price, and the batched lookup was
        // invoked exactly once for the whole set - not once per asset.
        assertThat(response.data).hasSize(3)
        assertThat(response.data.map { it.close })
            .allMatch { it.compareTo(BigDecimal("100.00")) == 0 }
        verify(priceService, times(1)).getLatestMarketData(remainingAssets, holidayDate)
    }
}