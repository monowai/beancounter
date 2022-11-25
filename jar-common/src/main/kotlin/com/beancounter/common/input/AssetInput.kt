package com.beancounter.common.input

import com.beancounter.common.model.Asset
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Payload to create an Asset.
 */
data class AssetInput(
    var market: String,
    var code: String,
    val name: String? = null, // Enricher should fill this in if it is not supplied
    @JsonIgnore var resolvedAsset: Asset? = null,
    val currency: String? = null,
    val category: String = "Equity" // Case in-sensitive assetCategory ID
)
