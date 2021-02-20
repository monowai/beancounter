package com.beancounter.position.model

import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse

/**
 * Rates and Prices necessary to values all positions.
 */
data class ValuationData(
    val priceResponse: PriceResponse? = null,
    val fxResponse: FxResponse? = null
)
