package com.beancounter.position.model

import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse

data class ValuationData(
    val priceResponse: PriceResponse? = null,
    val fxResponse: FxResponse? = null
)
