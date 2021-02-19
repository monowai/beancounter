package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.fasterxml.jackson.core.JsonProcessingException
import java.util.*

/**
 * Encapsulates routines to assist with asset keys and objects.
 *
 * @author mikeh
 * @since 2019-02-24
 */
class AssetUtils private constructor() {

    companion object {
        private val objectMapper = BcJson().objectMapper
        val USD = Currency("USD")

        /**
         * Takes a valid asset and returns a key for it.
         *
         * @param asset valid Asset
         * @return string that can be used to pull the asset from a map
         */
        @JvmStatic
        fun toKey(asset: Asset): String {
            return toKey(asset.code, asset.market.code)
        }

        @JvmStatic
        fun toKey(asset: AssetInput): String {
            return toKey(asset.code, asset.market)
        }

        @JvmStatic
        fun toKey(asset: String, market: String): String {
            return "$asset:$market"
        }

        @JvmStatic
        fun getMarket(code: String): Market {
            return Market(code, USD)
        }

        /**
         * Takes an Asset key and returns an AssetObject.
         *
         * @param key result of parseKey(Asset)
         * @return an Asset
         */
        @JvmStatic
        fun fromKey(key: String): Asset {
            val marketAsset = key.split(":").toTypedArray()
            if (marketAsset.size != 2) {
                throw BusinessException(String.format("Unable to parse the key %s", key))
            }
            return getAsset(marketAsset[1], marketAsset[0])
        }

        /**
         * Helper to create a simple Asset with a USD currency.
         *
         * @param marketCode marketCode
         * @param assetCode  assetCode
         * @return simple Asset.
         */
        @JvmStatic
        fun getAsset(marketCode: String, assetCode: String): Asset {
            val market = Market(marketCode, USD)
            val asset = getAsset(market, assetCode)
            asset.marketCode = null
            return asset
        }

        /**
         * Asset on a market.
         *
         * @param market    market to return
         * @param assetCode asset.code
         * @return asset on a market
         */
        @JvmStatic
        fun getAsset(market: Market, assetCode: String): Asset {
            val asset = Asset(assetCode)
            asset.id = assetCode
            asset.market = market
            asset.name = assetCode
            //asset.marketCode = market.code.toUpperCase()
            asset.marketCode = null
            return asset
        }

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
            val asset = getAsset(market, code)
            return objectMapper.readValue(objectMapper.writeValueAsString(asset), Asset::class.java)
        }

        @JvmStatic
        fun getAssetInput(market: String, code: String): AssetInput {
            return AssetInput(market, code, name = code)
        }

        @JvmStatic
        fun getAssetInput(asset: Asset): AssetInput {
            return AssetInput(asset.market.code, asset.code, name = asset.code, resolvedAsset = asset)
        }

    }

    init {
        throw UnsupportedOperationException("This is a utility class and cannot be instantiated")
    }
}