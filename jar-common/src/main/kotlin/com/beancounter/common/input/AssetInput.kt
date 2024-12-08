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
    // Enricher should fill this in if it is not supplied
    val name: String? = null,
    @JsonIgnore var resolvedAsset: Asset? = null,
    val currency: String? = null,
    // Case in-sensitive assetCategory ID
    val category: String = "Equity",
    val owner: String = "",
) {
    companion object {
        @JvmStatic
        val cashAsset = "CASH"

        @JvmStatic
        fun toCash(
            currency: Currency,
            name: String,
        ): AssetInput =
            AssetInput(
                "CASH",
                code = name,
                name = name,
                currency = currency.code,
                category = cashAsset,
            )

        @JvmStatic
        fun toRealEstate(
            currency: Currency,
            code: String,
            name: String,
            owner: String,
        ): AssetInput =
            AssetInput(
                "OFFM",
                code = code,
                name = name,
                currency = currency.code,
                category = AssetCategory.RE,
                owner = owner,
            )
    }
}
