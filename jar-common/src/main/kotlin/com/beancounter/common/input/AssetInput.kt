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
    // Expected annual return rate (as decimal, e.g., 0.03 for 3%). Default 3% if null.
    val expectedReturnRate: Double? = null
) {
    companion object {
        @JvmStatic
        val cashAsset = "CASH"

        @JvmStatic
        fun toCash(
            currency: Currency,
            name: String
        ): AssetInput =
            AssetInput(
                "CASH",
                code = name,
                name = name,
                currency = currency.code,
                category = cashAsset
            )

        @JvmStatic
        fun toRealEstate(
            currency: Currency,
            code: String,
            name: String,
            owner: String
        ): AssetInput =
            AssetInput(
                "PRIVATE",
                code = code,
                name = name,
                currency = currency.code,
                category = AssetCategory.RE,
                owner = owner
            )

        /**
         * Create a user-scoped bank account asset (savings, current, mortgage, etc.)
         */
        @JvmStatic
        fun toAccount(
            currency: Currency,
            code: String,
            name: String,
            owner: String
        ): AssetInput =
            AssetInput(
                "PRIVATE",
                code = code,
                name = name,
                currency = currency.code,
                category = AssetCategory.ACCOUNT,
                owner = owner
            )

        /**
         * Create a user-scoped retirement fund asset (pension, CPF, ILP, etc.)
         */
        @JvmStatic
        fun toPolicy(
            currency: Currency,
            code: String,
            name: String,
            owner: String,
            expectedReturnRate: Double? = null
        ): AssetInput =
            AssetInput(
                "PRIVATE",
                code = code,
                name = name,
                currency = currency.code,
                category = AssetCategory.POLICY,
                owner = owner,
                expectedReturnRate = expectedReturnRate
            )
    }
}