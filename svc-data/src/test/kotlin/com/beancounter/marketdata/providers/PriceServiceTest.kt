package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.alpha.AlphaEventService
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
    fun `should rebase provider-supplied previousClose on split ex-date`() {
        // Given: Alpha-style row on the ex-date — post-split close with a raw pre-split
        // previousClose already populated (not zero). Yesterday had no split.
        val today = LocalDate.of(2026, 4, 21)
        val yesterday = LocalDate.of(2026, 4, 20)

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = BigDecimal("76.65"),
                previousClose = BigDecimal("308.44"), // raw pre-split supplied by provider
                change = BigDecimal.ZERO,
                changePercent = BigDecimal.ZERO,
                split = BigDecimal("4")
            )
        val yesterdayMarketData =
            MarketData(
                asset = asset,
                priceDate = yesterday,
                close = BigDecimal("308.44")
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(asset, today)
        ).thenReturn(Optional.of(yesterdayMarketData))
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        val saved = priceService.handle(PriceResponse(listOf(todayMarketData))).toList()

        // previousClose rebased: 308.44 / 4 = 77.11
        assertTrue(
            saved[0].previousClose.compareTo(BigDecimal("77.11")) == 0,
            "previousClose should be 77.11 but was ${saved[0].previousClose}"
        )
        // change% reflects the real intra-day move (76.65 - 77.11) / 77.11 ≈ -0.6%
        assertTrue(
            saved[0].changePercent.compareTo(BigDecimal("-0.01")) > 0 &&
                saved[0].changePercent.compareTo(BigDecimal("0.01")) < 0,
            "changePercent should be small but was ${saved[0].changePercent}"
        )
    }

    @Test
    fun `should stamp ex-date split from Alpha when provider omits the flag`() {
        // Given: MarketStack-style ex-date row — close already on the post-split
        // basis, no split flag on the row, no previousClose supplied. The
        // Alpha-backed events feed knows the 6:1 split happened today.
        val today = LocalDate.of(2026, 4, 21)
        val yesterday = LocalDate.of(2026, 4, 20)

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = BigDecimal("81.50"),
                split = BigDecimal.ONE
            )
        val yesterdayMarketData =
            MarketData(
                asset = asset,
                priceDate = yesterday,
                close = BigDecimal("492.58")
            )

        val alphaEventService = mock(AlphaEventService::class.java)
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(
                listOf(
                    MarketData(
                        asset = asset,
                        priceDate = today,
                        close = BigDecimal.ZERO,
                        split = BigDecimal("6"),
                        dividend = BigDecimal.ZERO
                    )
                )
            )
        )
        priceService.setAlphaEventService(alphaEventService)

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(asset, today)
        ).thenReturn(Optional.of(yesterdayMarketData))
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        val saved = priceService.handle(PriceResponse(listOf(todayMarketData))).toList()

        // Split flag stamped from Alpha so SplitAdjuster recognises the ex-date.
        assertTrue(
            saved[0].split.compareTo(BigDecimal("6")) == 0,
            "split should be 6 but was ${saved[0].split}"
        )
        // previousClose rebased onto post-split basis (492.58 / 6 ≈ 82.0967)
        assertTrue(
            saved[0].previousClose.compareTo(BigDecimal("82.00")) > 0 &&
                saved[0].previousClose.compareTo(BigDecimal("82.20")) < 0,
            "previousClose should be ~82.10 but was ${saved[0].previousClose}"
        )
        // changePercent reflects the real intraday move, not -83%
        assertTrue(
            saved[0].changePercent.compareTo(BigDecimal("-0.05")) > 0 &&
                saved[0].changePercent.compareTo(BigDecimal("0.05")) < 0,
            "changePercent should be small but was ${saved[0].changePercent}"
        )
    }

    @Test
    fun `should not re-adjust when provider keeps split ratio sticky on days after the ex-date`() {
        // Given: yesterday was the ex-date already rebased (close=40, split=25 stored).
        // Today (7 Apr) the provider still carries split=25 but the close is already
        // post-split ($184) and should not be re-divided.
        val today = LocalDate.of(2026, 4, 7)
        val yesterday = LocalDate.of(2026, 4, 6)
        val todayClose = BigDecimal("184.00")
        val yesterdayClose = BigDecimal("176.19")

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = todayClose,
                split = BigDecimal("25"),
                previousClose = BigDecimal.ZERO,
                change = BigDecimal.ZERO,
                changePercent = BigDecimal.ZERO
            )
        val yesterdayMarketData =
            MarketData(
                asset = asset,
                priceDate = yesterday,
                close = yesterdayClose,
                split = BigDecimal("25")
            )

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(asset, today)
        ).thenReturn(Optional.of(yesterdayMarketData))
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        val saved = priceService.handle(PriceResponse(listOf(todayMarketData))).toList()

        assertEquals(1, saved.size)
        // Close stays at $184 (no extra division)
        assertTrue(
            saved[0].close.compareTo(BigDecimal("184")) == 0,
            "close should stay at 184 but was ${saved[0].close}"
        )
        // previousClose is yesterday's stored (already adjusted) close, not further divided
        assertTrue(
            saved[0].previousClose.compareTo(BigDecimal("176.19")) == 0,
            "previousClose should be 176.19 but was ${saved[0].previousClose}"
        )
    }

    @Test
    fun `should split-adjust close when provider supplies raw prices with split ratio`() {
        // Given: provider stored today's close as the raw pre-split $1000 (same as yesterday)
        // and carries a split ratio of 25 on the ex-date row.
        val today = LocalDate.of(2026, 4, 6)
        val yesterday = LocalDate.of(2026, 4, 5)
        val rawPreSplit = BigDecimal("1000.00")

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = rawPreSplit,
                open = rawPreSplit,
                high = rawPreSplit,
                low = rawPreSplit,
                split = BigDecimal("25"),
                previousClose = BigDecimal.ZERO,
                change = BigDecimal.ZERO,
                changePercent = BigDecimal.ZERO
            )
        val yesterdayMarketData =
            MarketData(asset = asset, priceDate = yesterday, close = rawPreSplit)

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(marketDataRepo.countByAssetIdAndPriceDate(asset.id, today)).thenReturn(0L)
        `when`(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(asset, today)
        ).thenReturn(Optional.of(yesterdayMarketData))
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        val saved = priceService.handle(PriceResponse(listOf(todayMarketData))).toList()

        assertEquals(1, saved.size)
        // Close / open / high / low rebased to post-split basis
        assertTrue(
            saved[0].close.compareTo(BigDecimal("40")) == 0,
            "close should be rebased to 40 but was ${saved[0].close}"
        )
        assertTrue(saved[0].open.compareTo(BigDecimal("40")) == 0)
        assertTrue(saved[0].high.compareTo(BigDecimal("40")) == 0)
        assertTrue(saved[0].low.compareTo(BigDecimal("40")) == 0)
        // previousClose also rebased; change/% reflect real movement (0 here)
        assertTrue(saved[0].previousClose.compareTo(BigDecimal("40")) == 0)
        assertTrue(saved[0].change.compareTo(BigDecimal.ZERO) == 0)
        assertTrue(saved[0].changePercent.compareTo(BigDecimal.ZERO) == 0)
    }

    @Test
    fun `should split-adjust previousClose when today carries a split ratio`() {
        // Given: Monday close $1000, Tuesday 25:1 split with post-split close $40
        val today = LocalDate.of(2026, 4, 6)
        val yesterday = LocalDate.of(2026, 4, 5)
        val yesterdayClose = BigDecimal("1000.00")
        val todayClose = BigDecimal("40.00")

        val todayMarketData =
            MarketData(
                asset = asset,
                priceDate = today,
                close = todayClose,
                split = BigDecimal("25"),
                previousClose = BigDecimal.ZERO,
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
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(asset, today)
        ).thenReturn(Optional.of(yesterdayMarketData))
        `when`(marketDataRepo.saveAll(anyList())).thenAnswer { it.arguments[0] }

        // When: Processing the price response for the ex-date
        val saved = priceService.handle(PriceResponse(listOf(todayMarketData))).toList()

        // Then: previousClose is rebased onto the post-split basis ($1000 / 25 = $40)
        assertEquals(1, saved.size)
        assertTrue(
            saved[0].previousClose.compareTo(BigDecimal("40")) == 0,
            "previousClose should be split-adjusted to 40.00 but was ${saved[0].previousClose}"
        )
        // And day-over-day change reflects only the real move (0 in this setup)
        assertTrue(
            saved[0].change.compareTo(BigDecimal.ZERO) == 0,
            "change should be 0 but was ${saved[0].change}"
        )
        assertTrue(
            saved[0].changePercent.compareTo(BigDecimal.ZERO) == 0,
            "changePercent should be 0 but was ${saved[0].changePercent}"
        )
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