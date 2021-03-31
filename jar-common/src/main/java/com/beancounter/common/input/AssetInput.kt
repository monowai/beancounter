package com.beancounter.common.input

import com.beancounter.common.model.Asset
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Payload to create an Asset.
 */
data class AssetInput(
    var market: String,
    var code: String,
    val name: String? = null,
    @JsonIgnore var resolvedAsset: Asset? = null
) {
    constructor(
        market: String,
        code: String
    ) :
        this(market = market, code = code, name = null, resolvedAsset = null)

    constructor(
        market: String,
        code: String,
        asset: Asset?
    ) :
        this(market = market, code = code, name = null, resolvedAsset = asset)

    constructor(asset: Asset) : this(market = asset.market.code, code = asset.code, resolvedAsset = asset)
}
