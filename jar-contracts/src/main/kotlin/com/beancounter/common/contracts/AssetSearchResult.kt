package com.beancounter.common.contracts

/**
 * The found asset.
 */
data class AssetSearchResult(
    var symbol: String,
    var name: String,
    var type: String,
    var region: String?,
    var currency: String?,
    var market: String? = null,
    var assetId: String? = null
)