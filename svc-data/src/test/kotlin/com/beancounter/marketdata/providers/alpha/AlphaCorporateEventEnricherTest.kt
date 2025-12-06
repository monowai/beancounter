package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.PercentUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for AlphaCorporateEventEnricher - ensures Global Quote prices are enriched
 * with split/dividend data from TIME_SERIES_DAILY_ADJUSTED.
 */
class AlphaCorporateEventEnricherTest {
    private val alphaEventService = mock(AlphaEventService::class.java)
    private val percentUtils = PercentUtils()
    private val enricher = AlphaCorporateEventEnricher(alphaEventService, percentUtils)

    private val market = Market("US")
    private val asset = Asset(id = "test-id", code = "SLK", market = market)
    private val priceDate = LocalDate.of(2025, 12, 5)

    @Test
    fun `should enrich Global Quote with split data when split exists for same date`() {
        // Given: A Global Quote MarketData with no split info (defaults to split=1)
        // The API returns unadjusted previous close, creating incorrect change
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                open = BigDecimal("99.00"),
                high = BigDecimal("101.00"),
                low = BigDecimal("98.00"),
                previousClose = BigDecimal("200.00"), // Unadjusted (pre-split)
                change = BigDecimal("-100.00"), // Wrong - not split-adjusted
                changePercent = BigDecimal("-0.50"), // Wrong - 50% drop instead of ~0%
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

        // Then: The split should be copied from adjusted data
        assertThat(enrichedData.split).isEqualTo(BigDecimal("2.0"))

        // And: The previous close should be adjusted for the split
        // Previous close 200 / split 2 = 100
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("100.00"))

        // And: The change should be recalculated (close - adjusted previousClose)
        // 100 - 100 = 0
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("0.00"))

        // And: The changePercent should be recalculated
        assertThat(enrichedData.changePercent).isEqualByComparingTo(BigDecimal.ZERO)
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
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("66.67"),
                previousClose = BigDecimal("100.00"), // Unadjusted
                change = BigDecimal("-33.33"),
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

        // Then: The split should be set
        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal("1.5"))

        // And: Previous close should be adjusted: 100 / 1.5 = 66.67
        assertThat(enrichedData.previousClose).isEqualByComparingTo(BigDecimal("66.67"))

        // And: Change should be ~0
        assertThat(enrichedData.change).isEqualByComparingTo(BigDecimal("0.00"))
    }
}