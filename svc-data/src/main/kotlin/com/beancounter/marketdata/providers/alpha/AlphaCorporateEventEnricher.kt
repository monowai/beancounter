package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.MathUtils
import com.beancounter.common.utils.PercentUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Enriches Global Quote MarketData with corporate event data (splits/dividends)
 * from TIME_SERIES_DAILY_ADJUSTED.
 *
 * The Global Quote API does not include split/dividend information. This service
 * fetches the adjusted time series data to:
 * 1. Detect splits and dividends that occurred on the price date
 * 2. Adjust previousClose when a split occurred (divide by split coefficient)
 * 3. Recalculate change and changePercent for split-adjusted values
 *
 * This ensures corporate events are properly detected and price changes are
 * accurately reported even when using the Global Quote API for current prices.
 */
@Service
class AlphaCorporateEventEnricher(
    private val alphaEventService: AlphaEventService,
    private val percentUtils: PercentUtils
) {
    /**
     * Enriches Global Quote MarketData with corporate event data.
     *
     * @param marketData The MarketData from Global Quote (lacks split/dividend info)
     * @return The same MarketData enriched with split/dividend data and adjusted prices
     */
    fun enrich(marketData: MarketData): MarketData {
        try {
            val events = alphaEventService.getEvents(marketData.asset)

            // Find event for the same date
            val eventForDate = events.data.find { it.priceDate == marketData.priceDate }

            if (eventForDate == null) {
                log.trace("No corporate event found for {} on {}", marketData.asset.code, marketData.priceDate)
                return marketData
            }

            // Enrich with dividend if present
            if (isDividend(eventForDate)) {
                marketData.dividend = eventForDate.dividend
                log.debug(
                    "Enriched {} with dividend {} on {}",
                    marketData.asset.code,
                    eventForDate.dividend,
                    marketData.priceDate
                )
            }

            // Enrich with split if present and adjust previousClose/change
            if (isSplit(eventForDate)) {
                marketData.split = eventForDate.split

                // Adjust previousClose by dividing by split coefficient
                // For a 2:1 split, previousClose of $200 becomes $100
                val adjustedPreviousClose =
                    MathUtils.divide(
                        marketData.previousClose,
                        eventForDate.split
                    )

                marketData.previousClose = adjustedPreviousClose

                // Recalculate change: close - adjusted previousClose
                val adjustedChange = marketData.close.subtract(adjustedPreviousClose)
                marketData.change = adjustedChange

                // Recalculate changePercent
                marketData.changePercent = percentUtils.percent(adjustedChange, adjustedPreviousClose)

                log.info(
                    "Enriched {} with split {} on {}, adjusted previousClose from pre-split to {}",
                    marketData.asset.code,
                    eventForDate.split,
                    marketData.priceDate,
                    adjustedPreviousClose
                )
            }

            return marketData
        } catch (e: Exception) {
            // Don't fail price retrieval if enrichment fails
            log.warn(
                "Failed to enrich {} with corporate events: {}",
                marketData.asset.code,
                e.message
            )
            return marketData
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AlphaCorporateEventEnricher::class.java)
    }
}