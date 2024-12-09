package com.beancounter.common.utils

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset

/**
 * Handles common user facing keys from an asset and a market.
 */
class AssetKeyUtils {
    companion object {
        /**
         * Takes a valid asset and returns a key for it.
         *
         * @param asset valid Asset
         * @return string that can be used to pull the asset from a map
         */
        @JvmStatic
        fun toKey(asset: Asset): String =
            toKey(
                asset.code,
                asset.market.code
            )

        @JvmStatic
        fun toKey(asset: AssetInput): String =
            toKey(
                asset.code,
                asset.market
            )

        @JvmStatic
        fun toKey(
            asset: String,
            market: String
        ): String = "$asset:$market"
    }
}