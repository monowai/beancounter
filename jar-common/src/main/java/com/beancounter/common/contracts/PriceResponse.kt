package com.beancounter.common.contracts

import com.beancounter.common.model.MarketData

data class PriceResponse(override val data: Collection<MarketData> = ArrayList()) : Payload<Collection<MarketData>> {

}