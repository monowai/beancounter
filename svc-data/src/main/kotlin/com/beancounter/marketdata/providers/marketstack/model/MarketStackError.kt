package com.beancounter.marketdata.providers.marketstack.model

/**
 * Error response from the MarketDataProvider.
 */
data class MarketStackError(
    val code: String,
    val message: String
)