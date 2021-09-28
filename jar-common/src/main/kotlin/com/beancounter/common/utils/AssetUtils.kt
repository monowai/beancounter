package com.beancounter.common.utils

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.fasterxml.jackson.core.JsonProcessingException

/**
 * Encapsulates routines to assist with asset keys and objects.
 *
 * @author mikeh
 * @since 2019-02-24
 */
class AssetUtils {

    companion object {
        private val objectMapper = BcJson().objectMapper

        /**
         * Asset on a market.
         *
         * @param market    market to return
         * @param assetCode asset.code
         * @return asset on a market
         */
        @JvmStatic
        fun getAsset(market: Market, assetCode: String) =
            Asset(id = assetCode, code = assetCode, name = assetCode, market = market)

        @JvmStatic
        fun split(assets: Collection<AssetInput>): Map<String, MutableCollection<AssetInput>> {
            val results: MutableMap<String, MutableCollection<AssetInput>> = HashMap()
            for (input in assets) {

                val marketAssets = results.computeIfAbsent(input.market) { ArrayList() }
                marketAssets.add(input)
            }
            return results
        }

        /**
         * Helper for tests that returns the "serialized" view of an asset.
         *
         * @param market marketCode
         * @param code   assetCode
         * @return asset object with all @JsonIgnore fields applied
         * @throws JsonProcessingException error
         */
        @JvmStatic
        @Throws(JsonProcessingException::class)
        fun getJsonAsset(market: String, code: String): Asset {
            val asset = getAsset(Market(market), code)
            return objectMapper.readValue(objectMapper.writeValueAsString(asset), Asset::class.java)
        }

        @JvmStatic
        fun getAssetInput(market: String, code: String) = AssetInput(market, code, name = code)

        @JvmStatic
        fun getAssetInput(asset: Asset) =
            AssetInput(market = asset.market.code, code = asset.code, name = asset.code, resolvedAsset = asset)
    }
}
