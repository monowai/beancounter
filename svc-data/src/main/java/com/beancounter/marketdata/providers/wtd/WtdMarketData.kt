package com.beancounter.marketdata.providers.wtd

import java.math.BigDecimal

/**
 * Contract representing WTD market data.
 */
data class WtdMarketData(
    val open: BigDecimal,
    val close: BigDecimal,
    val low: BigDecimal,
    val high: BigDecimal,
    val volume: Int
)
