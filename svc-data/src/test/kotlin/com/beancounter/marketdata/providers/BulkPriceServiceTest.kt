package com.beancounter.marketdata.providers

import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.PRIVATE_MARKET
import com.beancounter.marketdata.assets.AssetFinder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Bulk price retrieval is the hot path for wealth-performance prefetch. The implementation
 * uses a single window query (`findByAssetInAndPriceDateBetween`) and resolves exact +
 * nearest-prior fallback in memory. These tests cover that contract; the prior N+1
 * `findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc` path was removed because
 * it caused /api/prices/bulk to time out on the "ALL" wealth chart (incident 2026-05-18).
 */
class BulkPriceServiceTest {
    private lateinit var priceService: PriceService
    private lateinit var marketDataRepo: MarketDataRepo
    private val assetFinder = mock(AssetFinder::class.java)

    @BeforeEach
    fun setUp() {
        marketDataRepo = mock(MarketDataRepo::class.java)
        priceService = PriceService(marketDataRepo, CashUtils(), assetFinder)
    }

    @Test
    fun `getBulkMarketData returns empty map for empty inputs`() {
        val result = priceService.getBulkMarketData(emptyList(), emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `getBulkMarketData returns exact matches keyed by date`() {
        val date1 = LocalDate.of(2024, 1, 15)
        val date2 = LocalDate.of(2024, 2, 15)
        val assets = listOf(AAPL, MSFT)
        val dates = listOf(date1, date2)

        val md1 = MarketData(asset = AAPL, priceDate = date1, close = BigDecimal("150"))
        val md2 = MarketData(asset = MSFT, priceDate = date1, close = BigDecimal("400"))
        val md3 = MarketData(asset = AAPL, priceDate = date2, close = BigDecimal("155"))
        val md4 = MarketData(asset = MSFT, priceDate = date2, close = BigDecimal("410"))

        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(listOf(md1, md2, md3, md4))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result).hasSize(2)
        assertThat(result["2024-01-15"]).hasSize(2)
        assertThat(result["2024-02-15"]).hasSize(2)
    }

    @Test
    fun `getBulkMarketData falls back to nearest prior price for weekend dates`() {
        val weekend = LocalDate.of(2024, 1, 13) // Saturday
        val friday = LocalDate.of(2024, 1, 12)
        val assets = listOf(AAPL)
        val dates = listOf(weekend)

        // Window query returns Friday's row (within lookback); no Saturday row.
        val fridayMd = MarketData(asset = AAPL, priceDate = friday, close = BigDecimal("148"))
        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(listOf(fridayMd))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result).hasSize(1)
        assertThat(result["2024-01-13"]).hasSize(1)
        assertThat(result["2024-01-13"]!![0].close).isEqualByComparingTo(BigDecimal("148"))
    }

    @Test
    fun `getBulkMarketData handles mix of exact and fallback prices`() {
        val tradingDay = LocalDate.of(2024, 1, 15)
        val weekend = LocalDate.of(2024, 1, 13)
        val friday = LocalDate.of(2024, 1, 12)
        val assets = listOf(AAPL)
        val dates = listOf(tradingDay, weekend)

        val exactMd = MarketData(asset = AAPL, priceDate = tradingDay, close = BigDecimal("150"))
        val fallbackMd = MarketData(asset = AAPL, priceDate = friday, close = BigDecimal("148"))
        // Window contains both rows; one resolves exact, the other resolves nearest-prior.
        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(listOf(fallbackMd, exactMd))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result).hasSize(2)
        assertThat(result["2024-01-15"]).hasSize(1)
        assertThat(result["2024-01-15"]!![0].close).isEqualByComparingTo(BigDecimal("150"))
        assertThat(result["2024-01-13"]).hasSize(1)
        assertThat(result["2024-01-13"]!![0].close).isEqualByComparingTo(BigDecimal("148"))
    }

    @Test
    fun `getBulkMarketData rebases pre-split closes via SplitAdjuster`() {
        // Pre-split AAPL @ $500 should appear in the bulk response as the post-split
        // basis ($125) once a known 4-for-1 ex-date sits later in the window. Without
        // this, wealth/TWR charts span split dates with raw pre-split closes and show
        // an N× drop on the ex-date that never recovers.
        val preSplit = LocalDate.of(2020, 8, 28)
        val exDate = LocalDate.of(2020, 8, 31)
        val postSplit = LocalDate.of(2020, 9, 1)
        val assets = listOf(AAPL)
        val dates = listOf(preSplit, exDate, postSplit)

        val pre = MarketData(asset = AAPL, priceDate = preSplit, close = BigDecimal("500.00"))
        val ex =
            MarketData(asset = AAPL, priceDate = exDate, close = BigDecimal("125.00")).apply {
                split = BigDecimal("4")
            }
        val post = MarketData(asset = AAPL, priceDate = postSplit, close = BigDecimal("130.00"))

        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(listOf(pre, ex, post))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result["2020-08-28"]!![0].close)
            .isEqualByComparingTo(BigDecimal("125.00"))
        assertThat(result["2020-08-31"]!![0].close)
            .isEqualByComparingTo(BigDecimal("125.00"))
        assertThat(result["2020-09-01"]!![0].close)
            .isEqualByComparingTo(BigDecimal("130.00"))
    }

    @Test
    fun `getBulkMarketData returns empty list for date with no data in window`() {
        val date = LocalDate.of(2024, 1, 1)
        val assets = listOf(AAPL)

        // Window query returns nothing — no exact and no prior available.
        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(emptyList())

        val result = priceService.getBulkMarketData(assets, listOf(date))

        assertThat(result).hasSize(1)
        assertThat(result["2024-01-01"]).isEmpty()
    }

    @Test
    fun `bulk prices include private asset latest price when row predates lookback window`() {
        // PRIVATE-market assets (real estate, art, etc) get a single user-entered price
        // that can sit months old. The windowed load only reaches back
        // FALLBACK_LOOKBACK_DAYS, so such rows are otherwise invisible to the bulk
        // response and svc-position falls back to purchase-cost averaging (#1045 gap).
        val privateAsset = getTestAsset(PRIVATE_MARKET, "REALESTATE")
        val requestDate1 = LocalDate.of(2024, 1, 15)
        val requestDate2 = LocalDate.of(2024, 2, 15)
        val assets = listOf(privateAsset)
        val dates = listOf(requestDate1, requestDate2)

        // Row predates the window start (requestDate1 - FALLBACK_LOOKBACK_DAYS) by months.
        val oldPriceDate = requestDate1.minusMonths(5)
        val oldPrice =
            MarketData(asset = privateAsset, priceDate = oldPriceDate, close = BigDecimal("500000"))

        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(emptyList())
        whenever(
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(
                eq(privateAsset),
                eq(requestDate2)
            )
        ).thenReturn(Optional.of(oldPrice))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result["2024-01-15"]).hasSize(1)
        assertThat(result["2024-01-15"]!![0].close).isEqualByComparingTo(BigDecimal("500000"))
        assertThat(result["2024-02-15"]).hasSize(1)
        assertThat(result["2024-02-15"]!![0].close).isEqualByComparingTo(BigDecimal("500000"))
    }

    @Test
    fun `bulk prices leave non-private asset absent when its only row predates lookback window`() {
        // Behaviour for listed-market assets is unchanged: an out-of-window row with no
        // top-up path simply leaves the asset absent from the bulk response.
        val requestDate = LocalDate.of(2024, 1, 15)
        val assets = listOf(AAPL)

        whenever(marketDataRepo.findByAssetInAndPriceDateBetween(eq(assets), any(), any()))
            .thenReturn(emptyList())

        val result = priceService.getBulkMarketData(assets, listOf(requestDate))

        assertThat(result["2024-01-15"]).isEmpty()
        verify(
            marketDataRepo,
            org.mockito.kotlin.never()
        ).findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(any(), any())
    }
}