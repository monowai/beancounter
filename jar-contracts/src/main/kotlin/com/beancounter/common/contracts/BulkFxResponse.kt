package com.beancounter.common.contracts

/**
 * Response containing FX rates keyed by date string for O(1) per-date lookup.
 */
data class BulkFxResponse(
    override val data: Map<String, FxPairResults> = emptyMap()
) : Payload<Map<String, FxPairResults>>