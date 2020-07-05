package com.beancounter.common.contracts

import com.beancounter.common.model.Market

data class MarketResponse(override var data: Collection<Market>?) : Payload<Collection<Market?>?>