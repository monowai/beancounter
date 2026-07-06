package com.beancounter.client

import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse

/**
 * Return FX Rates in response to a Request.
 */
interface FxService {
    fun getRates(
        fxRequest: FxRequest,
        token: String = ""
    ): FxResponse

    fun getBulkRates(
        request: BulkFxRequest,
        token: String = ""
    ): BulkFxResponse = throw UnsupportedOperationException("Bulk FX not supported")
}