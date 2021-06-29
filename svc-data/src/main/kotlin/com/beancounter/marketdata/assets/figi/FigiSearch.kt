package com.beancounter.marketdata.assets.figi

/**
 * Search params into OpenFigi.
 */
data class FigiSearch(
    var idValue: String,
    var exchCode: String,
    var securityType2: String = "Common Stock",
    val includeUnlistedEquities: Boolean = true
) {
    var idType = "BASE_TICKER"
    var isIncludeUnlistedEquities = true

    init {
        isIncludeUnlistedEquities = includeUnlistedEquities
    }
}
