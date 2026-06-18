package com.beancounter.common.contracts

import com.beancounter.common.model.MarketData

/**
 * Response containing prices keyed by date string for O(1) per-date lookup.
 */
data class BulkPriceResponse(
    override val data: Map<String, Collection<MarketData>> = emptyMap()
) : Payload<Map<String, Collection<MarketData>>>