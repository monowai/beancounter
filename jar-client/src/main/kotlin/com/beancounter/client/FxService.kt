package com.beancounter.client

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse

/**
 * Return FX Rates in response to a Request.
 */
interface FxService {
    fun getRates(
        fxRequest: FxRequest,
        token: String = "",
    ): FxResponse
}
