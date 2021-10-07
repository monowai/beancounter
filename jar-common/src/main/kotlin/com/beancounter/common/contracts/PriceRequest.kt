package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.today

/**
 * Arguments by which prices on a date are located.
 */
data class PriceRequest(val date: String = today, val assets: Collection<AssetInput>, val currentMode: Boolean = true) {

    companion object {
        @JvmStatic
        fun of(asset: Asset, date: String = today): PriceRequest {
            val assetInputs: MutableCollection<AssetInput> = ArrayList()
            assetInputs.add(
                AssetInput(
                    market = asset.market.code,
                    code = asset.code,
                    name = asset.code,
                    resolvedAsset = asset
                )
            )
            return PriceRequest(date, assetInputs)
        }

        val dateUtils = DateUtils()

        @JvmStatic
        fun of(assetInput: AssetInput): PriceRequest {
            return PriceRequest(dateUtils.offsetDateString(today), arrayListOf(assetInput))
        }
    }
}
