package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.today
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal

/**
 * Arguments by which prices on a date are located.
 */
data class PriceRequest(
    val date: String = today,
    val assets: Collection<PriceAsset>,
    val currentMode: Boolean = true,
    val closePrice: BigDecimal = BigDecimal.ZERO,
) {
    @JsonIgnore
    var resolvedAsset: Asset? = null

    companion object {

        @JvmStatic
        fun of(date: String = today, assets: Collection<AssetInput>, currentMode: Boolean = true): PriceRequest {
            val priceAsset = arrayListOf<PriceAsset>()
            for (asset in assets) {
                priceAsset.add(parse(asset))
            }
            return PriceRequest(date, priceAsset, currentMode)
        }

        @JvmStatic
        fun of(assetInput: AssetInput): PriceRequest {
            return PriceRequest(dateUtils.offsetDateString(today), arrayListOf(parse(assetInput)))
        }

        private fun parse(assetInput: AssetInput): PriceAsset {
            return PriceAsset(market = assetInput.market, code = assetInput.code)
        }

        private fun parse(asset: Asset): PriceAsset {
            return PriceAsset(asset.market.code, asset.code)
        }

        @JvmStatic
        fun of(asset: Asset, date: String = today): PriceRequest {
            return PriceRequest(date, arrayListOf(PriceAsset(asset)))
        }

        fun of(date: String, positions: Positions, currentMode: Boolean = true): PriceRequest {
            val priceAsset = arrayListOf<PriceAsset>()
            for (position in positions.positions) {
                priceAsset.add(parse(position.value.asset))
            }
            return PriceRequest(date, priceAsset, currentMode)
        }

        val dateUtils = DateUtils()
    }
}
