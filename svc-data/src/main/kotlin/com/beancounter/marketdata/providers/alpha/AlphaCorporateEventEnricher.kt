package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
        // Index assets never have splits or dividends. TIME_SERIES_DAILY_ADJUSTED also
        // returns an error for index symbols (^GSPC, ^IXIC), so skip the upstream call.
        if (marketData.asset.category.equals(AssetCategory.INDEX, ignoreCase = true)) {
            return marketData
        }
        try {
            val events = alphaEventService.getEvents(marketData.asset)

            // Splits MUST match the ex-date exactly. Stamping a split coefficient
            // onto a neighbouring row leaves the system with two rows carrying
            // the same split value, which downstream chart logic interprets as
            // two ex-dates and double-divides pre-split history.
            val splitEvent =
                events.data.find {
                    it.priceDate.isEqual(marketData.priceDate) && isSplit(it)
                }

            // Dividends keep the ±1-day fallback to handle Global Quote vs
            // TIME_SERIES date offsets (pay-date vs ex-date conventions).
            // Filter to dividend-bearing events first so that a split-only row
            // on the same day doesn't shadow a dividend that exists ±1 day away.
            val dividendEvent =
                events.data.find {
                    it.priceDate.isEqual(marketData.priceDate) && isDividend(it)
                }
                    ?: events.data.find {
                        isDividend(it) &&
                            abs(ChronoUnit.DAYS.between(it.priceDate, marketData.priceDate)) == 1L
                    }

            if (dividendEvent != null) {
                marketData.dividend = dividendEvent.dividend
                log.debug(
                    "Enriched {} with dividend {} on {}",
                    marketData.asset.code,
                    dividendEvent.dividend,
                    marketData.priceDate
                )
            }

            // Note: We do NOT adjust previousClose since GLOBAL_QUOTE already returns
            // split-adjusted values. We only copy the split coefficient metadata.
            if (splitEvent != null) {
                marketData.split = splitEvent.split
                log.info(
                    "Enriched {} with split {} on {}",
                    marketData.asset.code,
                    splitEvent.split,
                    marketData.priceDate
                )
            }

            if (splitEvent == null && dividendEvent == null) {
                log.trace("No corporate event found for {} on {}", marketData.asset.code, marketData.priceDate)
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