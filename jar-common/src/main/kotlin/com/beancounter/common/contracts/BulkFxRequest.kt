package com.beancounter.common.contracts

import com.beancounter.common.model.IsoCurrencyPair

/**
 * Request for FX rates across a date range for multiple currency pairs.
 * Used by performance calculations to avoid per-date HTTP round-trips.
 */
data class BulkFxRequest(
    val startDate: String,
    val endDate: String,
    val pairs: Set<IsoCurrencyPair> = emptySet()
)