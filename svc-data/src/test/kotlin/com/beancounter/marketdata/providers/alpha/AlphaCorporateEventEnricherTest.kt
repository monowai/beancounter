package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for AlphaCorporateEventEnricher - ensures Global Quote prices are enriched
 * with split/dividend data from TIME_SERIES_DAILY_ADJUSTED.
 *
 * Note: GLOBAL_QUOTE already returns split-adjusted previousClose and change values,
 * so we only copy the split/dividend metadata without adjusting prices.
 */
class AlphaCorporateEventEnricherTest {
    private val alphaEventService = mock(AlphaEventService::class.java)
    private val enricher = AlphaCorporateEventEnricher(alphaEventService)

    private val market = Market("US")
    private val asset = Asset(id = "test-id", code = "SLK", market = market)
    private val priceDate = LocalDate.of(2025, 12, 5)

    @Test
    fun `should enrich Global Quote with split data when split exists for same date`() {
        // Given: A Global Quote MarketData with no split info (defaults to split=1)
        // Note: GLOBAL_QUOTE already returns split-adjusted previousClose and change
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                open = BigDecimal("99.00"),
                high = BigDecimal("101.00"),
                low = BigDecimal("98.00"),
                previousClose = BigDecimal("99.50"), // Already split-adjusted by GLOBAL_QUOTE
                change = BigDecimal("0.50"), // Already correct
                changePercent = BigDecimal("0.005"),
                volume = 1000000,
                source = "ALPHA"
            )

        // And: The TIME_SERIES_DAILY_ADJUSTED shows a 2:1 split on the same date
        val adjustedMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                split = BigDecimal("2.0"), // 2:1 split
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(adjustedMarketData))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: The split metadata should be copied from adjusted data
        assertThat(enrichedData.split).isEqualTo(BigDecimal("2.0"))

        // And: previousClose and change should remain unchanged
        // (GLOBAL_QUOTE already returns split-adjusted values)
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("99.50"))
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("0.50"))
        assertThat(enrichedData.changePercent).isEqualByComparingTo(BigDecimal("0.005"))
    }

    @Test
    fun `should enrich Global Quote with dividend data when dividend exists for same date`() {
        // Given: A Global Quote MarketData with no dividend info
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                previousClose = BigDecimal("100.50"),
                change = BigDecimal("-0.50"),
                source = "ALPHA"
            )

        // And: The TIME_SERIES_DAILY_ADJUSTED shows a dividend on the same date
        val adjustedMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                split = BigDecimal.ONE,
                dividend = BigDecimal("0.50") // $0.50 dividend
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(adjustedMarketData))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: The dividend should be copied from adjusted data
        assertThat(enrichedData.dividend).isEqualByComparingTo(BigDecimal("0.50"))

        // And: The split should remain at default (no adjustment needed)
        assertThat(enrichedData.split).isEqualTo(BigDecimal.ONE)

        // And: previousClose/change remain unchanged (dividend doesn't affect price directly)
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("100.50"))
    }

    @Test
    fun `should not modify MarketData when no corporate events exist`() {
        // Given: A Global Quote MarketData
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                previousClose = BigDecimal("99.00"),
                change = BigDecimal("1.00"),
                changePercent = BigDecimal("0.0101"),
                split = BigDecimal.ONE,
                dividend = BigDecimal.ZERO,
                source = "ALPHA"
            )

        // And: No corporate events exist
        `when`(alphaEventService.getEvents(asset)).thenReturn(PriceResponse(emptyList()))

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: All values should remain unchanged
        assertThat(enrichedData.close).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("99.00"))
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("1.00"))
        assertThat(enrichedData.split).isEqualTo(BigDecimal.ONE)
        assertThat(enrichedData.dividend).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `should not modify MarketData when event exists for different date`() {
        // Given: A Global Quote MarketData for today
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                previousClose = BigDecimal("200.00"), // Would be wrong if there was a split today
                change = BigDecimal("-100.00"),
                source = "ALPHA"
            )

        // And: A split exists but for a different date (yesterday)
        val yesterdayEvent =
            MarketData(
                asset = asset,
                priceDate = priceDate.minusDays(1), // Different date
                close = BigDecimal("100.00"),
                split = BigDecimal("2.0"),
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(yesterdayEvent))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: Values should remain unchanged (no event for the same date)
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("200.00"))
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("-100.00"))
        assertThat(enrichedData.split).isEqualTo(BigDecimal.ONE)
    }

    @Test
    fun `should handle fractional splits correctly`() {
        // Given: A Global Quote MarketData affected by a 3:2 split
        // Note: GLOBAL_QUOTE already returns split-adjusted previousClose and change
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("66.67"),
                previousClose = BigDecimal("66.00"), // Already split-adjusted by GLOBAL_QUOTE
                change = BigDecimal("0.67"), // Already correct
                source = "ALPHA"
            )

        // And: A 3:2 split (1.5 coefficient) on the same date
        val adjustedMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("66.67"),
                split = BigDecimal("1.5"), // 3:2 split
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(adjustedMarketData))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: The split metadata should be copied
        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal("1.5"))

        // And: previousClose and change should remain unchanged
        // (GLOBAL_QUOTE already returns split-adjusted values)
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("66.00"))
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("0.67"))
    }

    @Test
    fun `should handle XLE 2-for-1 split on December 5 2025`() {
        // XLE (Energy Select Sector SPDR ETF) had a 2:1 split on December 5, 2025
        // Pre-split price was approximately $173, post-split approximately $86.50
        val xleAsset = Asset(id = "xle-id", code = "XLE", market = market)
        val splitDate = LocalDate.of(2025, 12, 5)

        // Given: A Global Quote MarketData for XLE on the split date
        // GLOBAL_QUOTE returns split-adjusted previousClose (approx half of pre-split close)
        val globalQuoteMarketData =
            MarketData(
                asset = xleAsset,
                priceDate = splitDate,
                close = BigDecimal("86.50"), // Post-split close
                open = BigDecimal("86.25"),
                high = BigDecimal("87.00"),
                low = BigDecimal("85.75"),
                previousClose = BigDecimal("86.00"), // Already split-adjusted by GLOBAL_QUOTE
                change = BigDecimal("0.50"),
                changePercent = BigDecimal("0.0058"),
                volume = 15000000,
                source = "ALPHA"
            )

        // And: The TIME_SERIES_DAILY_ADJUSTED shows a 2:1 split on the same date
        val adjustedMarketData =
            MarketData(
                asset = xleAsset,
                priceDate = splitDate,
                close = BigDecimal("86.50"),
                split = BigDecimal("2.0"), // 2:1 split
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(xleAsset)).thenReturn(
            PriceResponse(listOf(adjustedMarketData))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: The split metadata should be copied
        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal("2.0"))

        // And: previousClose should remain at the split-adjusted value
        // (GLOBAL_QUOTE already returns split-adjusted previousClose)
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("86.00"))

        // And: change should be correct (no adjustment needed since GLOBAL_QUOTE handles it)
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("0.50"))
    }
}