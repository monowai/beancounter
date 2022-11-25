package com.beancounter.common.input

import com.beancounter.common.model.Asset
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Payload to create an Asset.
 */
data class AssetInput(
    var market: String,
    var code: String,
    val name: String? = null, // This will be the default name if it cannot be enriched by a data provider
    @JsonIgnore var resolvedAsset: Asset? = null,
    val currency: String? = null,
    val category: String = "Equity" // Case in-sensitive assetCategory ID
) {
    constructor(
        market: String,
        code: String
    ) :
        this(market = market, code = code, name = null, resolvedAsset = null)

    constructor(asset: Asset) : this(market = asset.market.code, code = asset.code, resolvedAsset = asset)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AssetInput

        if (market != other.market) return false
        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        var result = market.hashCode()
        result = 31 * result + code.hashCode()
        return result
    }
}
