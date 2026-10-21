package com.beancounter.marketdata.watchlist

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/** Scale of every watchlist price and fraction, matching MarketData.changePercent. */
internal const val PRICE_SCALE = 6

/**
 * Put a price stored on an earlier share basis onto today's: divide by the cumulative split
 * factor since it was observed (see PriceService.splitFactorsAfter).
 */
internal fun rebase(
    price: BigDecimal,
    factor: BigDecimal
): BigDecimal =
    if (factor.compareTo(BigDecimal.ONE) == 0) {
        price
    } else {
        price.divide(factor, PRICE_SCALE, RoundingMode.HALF_UP)
    }

/** The currency watchlist amounts are reported in: the asset's market currency. */
internal fun Asset.watchlistCurrency(): Currency = accountingType?.currency ?: market.currency