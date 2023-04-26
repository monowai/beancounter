package com.beancounter.common.input

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
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
    val category: String = "Equity", // Case in-sensitive assetCategory ID
) {
    companion object {
        @JvmStatic
        val realEstate = "RE"

        @JvmStatic
        fun toRealEstate(currency: Currency, name: String): AssetInput {
            return AssetInput(
                "PRIVATE",
                code = currency.code + ".$realEstate",
                name = name,
                currency = currency.code,
                category = realEstate
            )
        }
    }

}
