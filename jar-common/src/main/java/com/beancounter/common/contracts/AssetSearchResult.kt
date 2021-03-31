package com.beancounter.common.contracts

import org.springframework.boot.context.properties.ConstructorBinding

/**
 * The found asset.
 */
data class AssetSearchResult @ConstructorBinding constructor(
    var symbol: String?,
    var name: String?,
    var type: String?,
    var region: String?,
    var currency: String?
)
