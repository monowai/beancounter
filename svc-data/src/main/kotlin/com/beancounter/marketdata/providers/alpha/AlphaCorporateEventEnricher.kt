package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Enriches Global Quote MarketData with corporate event data (splits/dividends)
 * from TIME_SERIES_DAILY_ADJUSTED.
 *
 * The Global Quote API does not include split/dividend information, but it DOES
 * return split-adjusted previousClose and change values. This service fetches
 * the adjusted time series data to:
 * 1. Detect splits and dividends that occurred on the price date
 * 2. Copy the split coefficient and dividend amount to the MarketData
 *
 * Note: We do NOT adjust previousClose or change since GLOBAL_QUOTE already
 * returns split-adjusted values. Only the split/dividend metadata is enriched.
 */
@Service
class AlphaCorporateEventEnricher(
    private val alphaEventService: AlphaEventService
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
            val eventForDate = events.data.find { it.priceDate.isEqual(marketData.priceDate) }

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

            // Enrich with split if present
            // Note: We do NOT adjust previousClose since GLOBAL_QUOTE already returns
            // split-adjusted values. We only copy the split coefficient metadata.
            if (isSplit(eventForDate)) {
                marketData.split = eventForDate.split
                log.info(
                    "Enriched {} with split {} on {}",
                    marketData.asset.code,
                    eventForDate.split,
                    marketData.priceDate
                )
            }

            return marketData
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
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