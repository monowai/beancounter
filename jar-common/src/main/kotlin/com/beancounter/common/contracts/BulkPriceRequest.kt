package com.beancounter.common.contracts

/**
 * Request for prices across multiple dates and assets in a single call.
 * Used by performance calculations to avoid per-date HTTP round-trips.
 */
data class BulkPriceRequest(
    val dates: List<String> = listOf(),
    val assets: List<PriceAsset> = listOf()
)