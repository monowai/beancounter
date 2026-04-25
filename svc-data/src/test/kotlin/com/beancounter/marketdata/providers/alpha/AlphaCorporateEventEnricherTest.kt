package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.ProviderArguments
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
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
    fun `should NOT enrich split from adjacent day - splits must match exact ex-date`() {
        // Splits MUST match the ex-date exactly. Stamping the split coefficient
        // onto a neighbouring row leaves the system with two rows carrying the
        // same split, which downstream chart logic interprets as two ex-dates
        // and double-divides pre-split history.
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                previousClose = BigDecimal("100.00"),
                change = BigDecimal("0.00"),
                source = "ALPHA"
            )

        val yesterdayEvent =
            MarketData(
                asset = asset,
                priceDate = priceDate.minusDays(1),
                close = BigDecimal("100.00"),
                split = BigDecimal("2.0"),
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(yesterdayEvent))
        )

        val enrichedData = enricher.enrich(globalQuoteMarketData)

        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `should still enrich dividend from adjacent day`() {
        // Dividends keep the ±1-day fallback to handle Global Quote vs
        // TIME_SERIES date offsets (pay-date vs ex-date conventions).
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                source = "ALPHA"
            )

        val yesterdayEvent =
            MarketData(
                asset = asset,
                priceDate = priceDate.minusDays(1),
                close = BigDecimal("100.00"),
                split = BigDecimal.ONE,
                dividend = BigDecimal("0.40")
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(yesterdayEvent))
        )

        val enrichedData = enricher.enrich(globalQuoteMarketData)

        assertThat(enrichedData.dividend).isEqualByComparingTo(BigDecimal("0.40"))
        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `dividend fallback still fires when same-date event is split-only`() {
        // If the exact-date event carries only a split, the ±1-day dividend
        // fallback still has to look for a dividend on the neighbouring day.
        // Filtering events.data by isDividend() before the fallback prevents
        // a split-only same-date row from shadowing a real dividend.
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                source = "ALPHA"
            )

        val splitOnlyToday =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                split = BigDecimal("2.0"),
                dividend = BigDecimal.ZERO
            )
        val dividendYesterday =
            MarketData(
                asset = asset,
                priceDate = priceDate.minusDays(1),
                close = BigDecimal("100.00"),
                split = BigDecimal.ONE,
                dividend = BigDecimal("0.30")
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(splitOnlyToday, dividendYesterday))
        )

        val enrichedData = enricher.enrich(globalQuoteMarketData)

        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(enrichedData.dividend).isEqualByComparingTo(BigDecimal("0.30"))
    }

    @Test
    fun `should not stamp split onto day after ex-date (VO regression)`() {
        // VO 4:1 split on 2026-04-21. Refreshing the price for 2026-04-22
        // must NOT carry the split=4 stamp forward, otherwise downstream
        // chart logic sees two ex-dates and double-divides earlier history.
        val voAsset = Asset(id = "vo-id", code = "VO", market = market)
        val exDate = LocalDate.of(2026, 4, 21)
        val dayAfter = LocalDate.of(2026, 4, 22)

        val dayAfterQuote =
            MarketData(
                asset = voAsset,
                priceDate = dayAfter,
                close = BigDecimal("76.56"),
                source = "ALPHA"
            )

        val splitEventOnExDate =
            MarketData(
                asset = voAsset,
                priceDate = exDate,
                close = BigDecimal("76.625"),
                split = BigDecimal("4.0"),
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(voAsset)).thenReturn(
            PriceResponse(listOf(splitEventOnExDate))
        )

        val enrichedData = enricher.enrich(dayAfterQuote)

        assertThat(enrichedData.split).isEqualByComparingTo(BigDecimal.ONE)
    }

    @Test
    fun `should not match event outside 1-day window`() {
        // Given: A Global Quote MarketData for today
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                previousClose = BigDecimal("200.00"),
                change = BigDecimal("-100.00"),
                source = "ALPHA"
            )

        // And: A split exists but 2 days ago (outside the +/-1 day window)
        val twoDaysAgoEvent =
            MarketData(
                asset = asset,
                priceDate = priceDate.minusDays(2),
                close = BigDecimal("100.00"),
                split = BigDecimal("2.0"),
                dividend = BigDecimal.ZERO
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(twoDaysAgoEvent))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: Values should remain unchanged (event is outside window)
        assertThat(enrichedData.split).isEqualTo(BigDecimal.ONE)
    }

    @Test
    fun `should prefer exact date match over adjacent day`() {
        // Given: A Global Quote MarketData for today
        val globalQuoteMarketData =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                source = "ALPHA"
            )

        // And: Both an exact-date dividend and an adjacent-day dividend exist
        val yesterdayEvent =
            MarketData(
                asset = asset,
                priceDate = priceDate.minusDays(1),
                close = BigDecimal("100.00"),
                dividend = BigDecimal("0.25")
            )
        val exactDateEvent =
            MarketData(
                asset = asset,
                priceDate = priceDate,
                close = BigDecimal("100.00"),
                dividend = BigDecimal("0.50")
            )
        `when`(alphaEventService.getEvents(asset)).thenReturn(
            PriceResponse(listOf(yesterdayEvent, exactDateEvent))
        )

        // When: Enriching the Global Quote data
        val enrichedData = enricher.enrich(globalQuoteMarketData)

        // Then: The exact-date event's dividend should be used, not yesterday's
        assertThat(enrichedData.dividend).isEqualByComparingTo(BigDecimal("0.50"))
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

    @Test
    fun `adapter should skip enrichment for historical requests`() {
        // Given: An AlphaPriceAdapter with a mock enricher and real AlphaConfig ObjectMapper
        val mockEnricher = mock(AlphaCorporateEventEnricher::class.java)
        val config = AlphaConfig()
        val adapter = AlphaPriceAdapter(config, mockEnricher)

        val providerArguments = mock(ProviderArguments::class.java)
        `when`(providerArguments.getAssets(0)).thenReturn(listOf("SLK"))
        `when`(providerArguments.getAsset("SLK")).thenReturn(asset)

        // Valid Alpha Global Quote JSON
        val json = globalQuoteJson()

        // When: Processing with currentMode=false (historical backfill)
        val results = adapter[providerArguments, 0, json, false]

        // Then: Enricher should NOT be called
        verify(mockEnricher, never()).enrich(any())
        assertThat(results).hasSize(1)
    }

    @Test
    fun `adapter should enrich for current-mode requests`() {
        // Given: Same setup but currentMode=true
        val mockEnricher = mock(AlphaCorporateEventEnricher::class.java)
        val config = AlphaConfig()
        val adapter = AlphaPriceAdapter(config, mockEnricher)

        val providerArguments = mock(ProviderArguments::class.java)
        `when`(providerArguments.getAssets(0)).thenReturn(listOf("SLK"))
        `when`(providerArguments.getAsset("SLK")).thenReturn(asset)

        val json = globalQuoteJson()

        `when`(mockEnricher.enrich(any())).thenAnswer { it.arguments[0] }

        // When: Processing with currentMode=true
        val results = adapter[providerArguments, 0, json, true]

        // Then: Enricher SHOULD be called
        verify(mockEnricher).enrich(any())
        assertThat(results).hasSize(1)
    }

    private fun globalQuoteJson(): String =
        """{"Global Quote":{""" +
            """"01. symbol":"SLK",""" +
            """"02. open":"99.00",""" +
            """"03. high":"101.00",""" +
            """"04. low":"98.00",""" +
            """"05. price":"100.00",""" +
            """"06. volume":"1000000",""" +
            """"07. latest trading day":"2025-12-05",""" +
            """"08. previous close":"99.50",""" +
            """"09. change":"0.50",""" +
            """"10. change percent":"0.50%"}}"""
}