package com.beancounter.common.contracts

import com.beancounter.common.model.Asset
import com.fasterxml.jackson.annotation.JsonIgnore

data class PriceAsset(val market: String, val code: String, @JsonIgnore var resolvedAsset: Asset? = null) {
    constructor(asset: Asset) : this(asset.market.code, asset.code, asset)
}
