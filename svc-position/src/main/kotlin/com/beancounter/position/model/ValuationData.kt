package com.beancounter.position.model

import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse

/**
 * Rates and Prices necessary to values all positions.
 */
data class ValuationData(
    val priceResponse: PriceResponse = PriceResponse(),
    val fxResponse: FxResponse = FxResponse(),
)
