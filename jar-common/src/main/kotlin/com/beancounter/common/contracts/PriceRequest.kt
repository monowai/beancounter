package com.beancounter.common.contracts

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal

/**
 * Arguments by which prices on a date are located.
 */
data class PriceRequest(
    val date: String = TODAY,
    val assets: List<PriceAsset>,
    val currentMode: Boolean = date == TODAY,
    val closePrice: BigDecimal = BigDecimal.ZERO,
) {
    @JsonIgnore
    var resolvedAsset: Asset? = null

    /**
     * Helper methods to deal with PriceRequest Objects
     */
    companion object {
        @JvmStatic
        fun of(
            date: String = TODAY,
            assets: Collection<AssetInput>,
            currentMode: Boolean = date == TODAY,
        ): PriceRequest {
            val priceAssets = assets.map { PriceAsset(market = it.market, code = it.code) }
            return PriceRequest(date, priceAssets, currentMode)
        }

        @JvmStatic
        fun of(input: AssetInput): PriceRequest =
            PriceRequest(
                TODAY,
                listOf(PriceAsset(market = input.market, code = input.code)),
            )

        @JvmStatic
        fun of(
            asset: Asset,
            date: String = TODAY,
        ): PriceRequest = PriceRequest(date, listOf(PriceAsset(asset)))

        fun of(
            date: String = TODAY,
            positions: Positions,
            currentMode: Boolean = date == TODAY,
        ): PriceRequest {
            val priceAssets =
                positions.positions.values.map {
                    PriceAsset(
                        it.asset.market.code,
                        it.asset.code,
                        assetId = it.asset.id,
                    )
                }
            return PriceRequest(date, priceAssets, currentMode)
        }
    }
}
