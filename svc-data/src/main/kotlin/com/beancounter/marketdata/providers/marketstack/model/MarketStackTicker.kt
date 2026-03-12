package com.beancounter.marketdata.providers.marketstack.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a ticker from MarketStack's exchange tickers endpoint.
 */
data class MarketStackTicker(
    val name: String,
    val symbol: String,
    @param:JsonProperty("has_intraday")
    val hasIntraday: Boolean = false,
    @param:JsonProperty("has_eod")
    val hasEod: Boolean = true
)

/**
 * Exchange data containing tickers from MarketStack's exchange tickers endpoint.
 */
data class MarketStackExchangeData(
    val name: String? = null,
    val acronym: String? = null,
    val mic: String? = null,
    val tickers: List<MarketStackTicker> = emptyList()
)

/**
 * Response from MarketStack's exchange tickers endpoint.
 */
data class MarketStackTickerResponse(
    val data: MarketStackExchangeData? = null,
    val error: MarketStackError? = null
)