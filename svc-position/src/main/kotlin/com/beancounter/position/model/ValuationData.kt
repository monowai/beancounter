package com.beancounter.position.model

import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceResponse

/**
 * Rates, prices and classifications necessary to value all positions.
 */
data class ValuationData(
    val priceResponse: PriceResponse = PriceResponse(),
    val fxResponse: FxResponse = FxResponse(),
    val classifications: BulkClassificationResponse = BulkClassificationResponse()
)