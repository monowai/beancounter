package com.beancounter.client

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse

interface FxService {
    fun getRates(fxRequest: FxRequest): FxResponse
}