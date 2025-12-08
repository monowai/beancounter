package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.event.EventProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Test the PriceService using Mocks
 */
class PriceServiceTest {
    private lateinit var priceService: PriceService
    private lateinit var marketDataRepo: MarketDataRepo
    private lateinit var cashUtils: CashUtils
    private lateinit var eventProducer: EventProducer
    private val assetFinder = mock(AssetFinder::class.java)
    private val asset = Asset(code = "1", market = NASDAQ)

    @BeforeEach
    fun setUp() {
        marketDataRepo = mock(MarketDataRepo::class.java)
        cashUtils = mock(CashUtils::class.java)
        eventProducer = mock(EventProducer::class.java)
        priceService = PriceService(marketDataRepo, cashUtils, assetFinder)
        priceService.setEventWriter(eventProducer)
        `when`(assetFinder.find(asset.id)).thenReturn(asset)
    }

    @Test
    fun `test getMarketData with existing data`() {
        val date = LocalDate.now()
        val marketData = MarketData(asset, date, BigDecimal.TEN)

        `when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)).thenReturn(Optional.of(marketData))

        val result = priceService.getMarketData(asset.id, date)

        assertTrue(result.isPresent)
        assertEquals(marketData, result.get())
    }

    @Test
    fun `test getMarketData with non-existing data`() {
        val date = LocalDate.now()

        `when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)).thenReturn(Optional.empty())

        val result = priceService.getMarketData(asset.id, date)

        assertFalse(result.isPresent)
    }

    @Test
    fun `test handle no close price is not saved`() {
        val localDate = LocalDate.now()
        val marketData = MarketData(asset, LocalDate.now(), BigDecimal.ZERO)
        val priceResponse = PriceResponse(listOf(marketData))

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(
            marketDataRepo.findByAssetIdAndPriceDate(
                "1",
                localDate
            )
        ).thenReturn(Optional.empty())
        `when`(marketDataRepo.saveAll(anyList())).thenReturn(listOf(marketData))

        val result = priceService.handle(priceResponse)
        verify(marketDataRepo, times(0)).saveAll(anyList())
        assertEquals(0, result.count())
    }

    @Test
    fun `test purge all market data`() {
        priceService.purge()

        verify(marketDataRepo, times(1)).deleteAll()
    }

    @Test
    fun `test purge specific market data`() {
        val marketData = MarketData(asset, LocalDate.now(), BigDecimal.TEN)

        priceService.purge(marketData)

        verify(marketDataRepo, times(1)).deleteById(marketData.id)
    }

    @Test
    fun `should calculate previousClose from previous day data when API returns zero`() {
        // Given: Market data with no previousClose from API (zero)
        val today = LocalDate.of(2025, 12, 3)
        val yesterday = LocalDate.of(2025, 12, 2)
        val todayPrice = BigDecimal("100.50")
        val yesterdayClose = BigDecimal("99.00")

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = todayPrice,
                previousClose = BigDecimal.ZERO, // API didn't provide previousClose
                change = BigDecimal.ZERO,
                changePercent = BigDecimal.ZERO
            )

        val yesterdayMarketData =
            MarketData(
                asset = asset,
                priceDate = yesterday,
                close = yesterdayClose
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
                asset,
                today
            )
        ).thenReturn(Optional.of(yesterdayMarketData))
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        // When: Processing the price response
        val result = priceService.handle(PriceResponse(listOf(todayMarketData)))

        // Then: previousClose, change, and changePercent should be calculated
        val savedData = result.toList()
        assertEquals(1, savedData.size)
        assertEquals(yesterdayClose, savedData[0].previousClose)
        assertEquals(BigDecimal("1.50"), savedData[0].change)
        // changePercent = 1.50 / 99.00 = 0.015152...
        assertTrue(savedData[0].changePercent.compareTo(BigDecimal("0.015")) > 0)
    }

    @Test
    fun `should not override previousClose when API provides it`() {
        // Given: Market data with previousClose from API
        val today = LocalDate.of(2025, 12, 3)
        val apiPreviousClose = BigDecimal("98.50")

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = BigDecimal("100.50"),
                previousClose = apiPreviousClose, // API provided previousClose
                change = BigDecimal("2.00"),
                changePercent = BigDecimal("0.0203")
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        // When: Processing the price response
        val result = priceService.handle(PriceResponse(listOf(todayMarketData)))

        // Then: previousClose should remain as provided by API
        val savedData = result.toList()
        assertEquals(1, savedData.size)
        assertEquals(apiPreviousClose, savedData[0].previousClose)
        assertEquals(BigDecimal("2.00"), savedData[0].change)
    }

    @Test
    fun `should leave previousClose as zero when no previous day data exists`() {
        // Given: Market data with no previousClose and no previous data in DB
        val today = LocalDate.of(2025, 12, 3)

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = BigDecimal("100.50"),
                previousClose = BigDecimal.ZERO,
                change = BigDecimal.ZERO,
                changePercent = BigDecimal.ZERO
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
                asset,
                today
            )
        ).thenReturn(Optional.empty())
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        // When: Processing the price response
        val result = priceService.handle(PriceResponse(listOf(todayMarketData)))

        // Then: previousClose should remain zero
        val savedData = result.toList()
        assertEquals(1, savedData.size)
        assertEquals(BigDecimal.ZERO, savedData[0].previousClose)
    }

    @Test
    fun `should reject zero close price with different decimal representations`() {
        // Given: Market data with various zero representations
        val today = LocalDate.of(2025, 12, 3)
        val zeroVariants =
            listOf(
                BigDecimal.ZERO,
                BigDecimal("0"),
                BigDecimal("0.0"),
                BigDecimal("0.00"),
                BigDecimal("0.000000")
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)

        for (zeroValue in zeroVariants) {
            val marketData =
                MarketData(
                    asset = asset,
                    priceDate = today,
                    close = zeroValue
                )

            // When: Processing the price response with zero close
            val result = priceService.handle(PriceResponse(listOf(marketData)))

            // Then: No data should be saved
            assertEquals(0, result.count(), "Zero price $zeroValue should not be saved")
        }

        // Verify saveAll was never called
        verify(marketDataRepo, times(0)).saveAll(anyList())
    }

    @Test
    fun `should save only valid prices when batch contains zero prices`() {
        // Given: A batch with mixed valid and zero prices
        val today = LocalDate.of(2025, 12, 3)
        val validAsset1 = Asset(code = "VALID1", market = NASDAQ)
        val validAsset2 = Asset(code = "VALID2", market = NASDAQ)
        val zeroAsset = Asset(code = "ZERO", market = NASDAQ)

        val validPrice1 = MarketData(asset = validAsset1, priceDate = today, close = BigDecimal("100.50"))
        val zeroPrice = MarketData(asset = zeroAsset, priceDate = today, close = BigDecimal.ZERO)
        val validPrice2 = MarketData(asset = validAsset2, priceDate = today, close = BigDecimal("200.75"))

        `when`(cashUtils.isCash(validAsset1)).thenReturn(false)
        `when`(cashUtils.isCash(validAsset2)).thenReturn(false)
        `when`(cashUtils.isCash(zeroAsset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(validAsset1.id, today)).thenReturn(0L)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(validAsset2.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
                validAsset1,
                today
            )
        ).thenReturn(Optional.empty())
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
                validAsset2,
                today
            )
        ).thenReturn(Optional.empty())
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        // When: Processing a batch with mixed prices
        val result = priceService.handle(PriceResponse(listOf(validPrice1, zeroPrice, validPrice2)))

        // Then: Only valid prices should be saved (2 out of 3)
        val savedData = result.toList()
        assertEquals(2, savedData.size, "Only valid (non-zero) prices should be saved")
        assertTrue(
            savedData.none { it.close.compareTo(BigDecimal.ZERO) == 0 },
            "No zero prices should be in saved data"
        )
    }

    @Test
    fun `should reject negative close prices as invalid`() {
        // Given: Market data with negative close price (invalid data from provider)
        val today = LocalDate.of(2025, 12, 3)
        val negativePrice =
            MarketData(
                asset = asset,
                priceDate = today,
                close = BigDecimal("-10.50")
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)

        // When: Processing the price response with negative close
        val result = priceService.handle(PriceResponse(listOf(negativePrice)))

        // Then: No data should be saved
        assertEquals(0, result.count(), "Negative price should not be saved")
        verify(marketDataRepo, times(0)).saveAll(anyList())
    }
}