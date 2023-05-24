package com.beancounter.common.input

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
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
        val offMarket = "OFFM"

        @JvmStatic
        val cashAsset = "CASH"

        @JvmStatic
        fun toCash(currency: Currency, name: String): AssetInput {
            return AssetInput(
                "CASH",
                name = name,
                currency = currency.code,
                code = name,
                category = cashAsset,
            )
        }

        @JvmStatic
        fun toRealEstate(currency: Currency, code: String, name: String): AssetInput {
            return AssetInput(
                "OFFM",
                code = code.uppercase(),
                name = name,
                currency = currency.code,
                category = AssetCategory.RE,
            )
        }
    }
}
