package com.beancounter.marketdata.assets.figi

/**
 * FIGI API contract.
 */
data class FigiResponse(
    var data: Collection<FigiAsset>?,
    var error: String?,
)
