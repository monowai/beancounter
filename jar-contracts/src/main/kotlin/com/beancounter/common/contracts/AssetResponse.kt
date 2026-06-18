package com.beancounter.common.contracts

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData

/**
 * Response to an asset request.
 * Optionally includes current price data when requested via includePrice query parameter.
 */
data class AssetResponse(
    override val data: Asset,
    val price: MarketData? = null
) : Payload<Asset?>