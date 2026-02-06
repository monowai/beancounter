package com.beancounter.marketdata.assets.figi

/**
 * Search params into OpenFigi /v3/mapping endpoint.
 */
data class FigiSearch(
    var idType: String = "TICKER",
    var idValue: String,
    var exchCode: String? = null,
    var securityType2: String? = null,
    val includeUnlistedEquities: Boolean = true
)