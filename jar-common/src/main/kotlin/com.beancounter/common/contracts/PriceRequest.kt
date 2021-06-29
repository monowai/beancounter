package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils.Companion.today

/**
 * Arguments by which prices on a date are located.
 */
data class PriceRequest(val date: String = today, val assets: Collection<AssetInput>) {

    companion object {
        @JvmStatic
        fun of(asset: Asset): PriceRequest {
            val assetInputs: MutableCollection<AssetInput> = ArrayList()
            assetInputs.add(AssetUtils.getAssetInput(asset))
            return PriceRequest(today, assetInputs)
        }

        @JvmStatic
        fun of(assetInput: AssetInput): PriceRequest {
            val assetInputs: MutableCollection<AssetInput> = ArrayList()
            assetInputs.add(assetInput)
            return PriceRequest(today, assetInputs)
        }
    }
}
