package com.beancounter.common.input

import com.beancounter.common.model.Asset
import com.fasterxml.jackson.annotation.JsonIgnore

data class AssetInput(var market: String,
                      var code: String,
                      val name: String? = null,
                      @JsonIgnore var resolvedAsset: Asset? = null) {
    constructor(market: String,
                code: String)
            : this(market, code, null, null)

    constructor(market: String,
                code: String,
                asset: Asset?)
            : this(market, code, null, asset)

}