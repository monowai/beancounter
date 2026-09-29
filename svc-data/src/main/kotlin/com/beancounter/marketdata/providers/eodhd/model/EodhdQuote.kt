package com.beancounter.marketdata.providers.eodhd.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * EODHD live (delayed) quote — 15-20 minutes behind for stocks.
 *
 * Endpoint: GET /api/real-time/{SYMBOL.EXCHANGE}?fmt=json
 *
 * EODHD sends the string "NA" in place of any number it doesn't have, so values are
 * held loosely and parsed by the caller.
 */
data class EodhdQuote(
    val code: String? = null,
    val timestamp: Any? = null,
    val open: Any? = null,
    val high: Any? = null,
    val low: Any? = null,
    val close: Any? = null,
    val volume: Any? = null,
    val previousClose: Any? = null,
    val change: Any? = null,
    @param:JsonProperty("change_p")
    val changePercent: Any? = null
)