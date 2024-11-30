package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal

data class PriceRequest(
    val date: String = TODAY,
    val assets: List<PriceAsset> = listOf(),
    val currentMode: Boolean = date == TODAY,
    val closePrice: BigDecimal = BigDecimal.ZERO,
) {
    @JsonIgnore
    var resolvedAsset: Asset? = null

    companion object {
        @JvmStatic
        fun of(
            date: String = TODAY,
            assets: Collection<AssetInput>,
            currentMode: Boolean = true,
        ): PriceRequest {
            return PriceRequest(date, assets.map { parse(it) }, currentMode)
        }

        @JvmStatic
        fun of(assetInput: AssetInput): PriceRequest {
            return PriceRequest(TODAY, listOf(parse(assetInput)))
        }

        private fun parse(input: AssetInput): PriceAsset {
            return PriceAsset(market = input.market, code = input.code)
        }

        private fun parse(asset: Asset): PriceAsset {
            return PriceAsset(asset.market.code, asset.code, assetId = asset.id)
        }

        @JvmStatic
        fun of(
            asset: Asset,
            date: String = TODAY,
        ): PriceRequest {
            return PriceRequest(date, listOf(PriceAsset(asset)))
        }

        fun of(
            date: String,
            positions: Positions,
            currentMode: Boolean = true,
        ): PriceRequest {
            return PriceRequest(date, positions.positions.values.map { parse(it.asset) }, currentMode)
        }
    }
}
