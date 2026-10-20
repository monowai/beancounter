package com.beancounter.common.contracts

import com.beancounter.common.model.AssetTraits
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The found asset. The traits are derived from [type] and [market] (a hit with no
 * market is treated as public) and are read-only in JSON.
 */
data class AssetSearchResult(
    var symbol: String,
    var name: String,
    var type: String,
    var region: String?,
    var currency: String?,
    var market: String? = null,
    var assetId: String? = null
) {
    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val chartable: Boolean
        get() = AssetTraits.chartable(type)

    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val watchable: Boolean
        get() = AssetTraits.watchable(type, market)

    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val hasNews: Boolean
        get() = AssetTraits.hasNews(type, market)

    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val displayCode: String
        get() = AssetTraits.displayCode(symbol)
}