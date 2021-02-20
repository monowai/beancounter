package com.beancounter.marketdata.assets.figi

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
