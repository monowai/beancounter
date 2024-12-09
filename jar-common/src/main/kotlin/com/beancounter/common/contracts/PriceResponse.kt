package com.beancounter.common.contracts

import com.beancounter.common.model.MarketData

/**
 * Response to a PriceRequest.
 */
data class PriceResponse(
    override val data: Collection<MarketData> = arrayListOf()
) : Payload<Collection<MarketData>>