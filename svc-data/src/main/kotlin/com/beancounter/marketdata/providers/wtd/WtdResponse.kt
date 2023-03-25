package com.beancounter.marketdata.providers.wtd

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Encapsulates the responses from the MarketDataProvider.
 *
 * @author mikeh
 * @since 2019-03-12
 */
data class WtdResponse constructor(
    val date: String? = null,
    val data: Map<String, WtdMarketData> = HashMap(),
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("Message")
    val message: String?,
) {
    constructor(date: String, prices: Map<String, WtdMarketData> = HashMap()) : this(date, prices, null)
}
