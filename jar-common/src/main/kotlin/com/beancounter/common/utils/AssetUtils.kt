package com.beancounter.common.utils

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.core.JsonProcessingException

/**
 * Encapsulates routines to assist with asset keys and objects.
 *
 * @author mikeh
 * @since 2019-02-24
 */
class AssetUtils {
    companion object {
        /**
         * Asset on a market.
         *
         * @param market    market to return
         * @param code asset.code
         * @return asset on a market
         */
        @JvmStatic
        fun getTestAsset(
            market: Market,
            code: String,
        ) = Asset(
            code = code,
            id = code,
            name = code,
            market = market,
            status = Status.Active,
        )

        @JvmStatic
        fun split(assets: Collection<PriceAsset>): Map<String, List<PriceAsset>> = assets.groupByTo(mutableMapOf(), PriceAsset::market)

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
        fun getJsonAsset(
            market: String,
            code: String,
        ): Asset {
            val asset = getTestAsset(Market(market), code)
            return objectMapper.readValue(objectMapper.writeValueAsString(asset), Asset::class.java)
        }

        @JvmStatic
        fun getAssetInput(
            market: String,
            code: String,
        ) = AssetInput(market, code, name = code)

        @JvmStatic
        fun getAssetInput(asset: Asset) =
            AssetInput(market = asset.market.code, code = asset.code, name = asset.code, resolvedAsset = asset)

        /**
         * Template for a generic cash balance asset.
         */
        @JvmStatic
        fun getCash(currency: String) =
            AssetInput(
                market = "CASH",
                code = currency,
                name = "$currency Balance",
                currency = currency,
                category = "cash",
            )
    }
}
