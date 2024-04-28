package com.beancounter.position.service

import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Facade to support async requests.
 */
@Service
class AsyncMdService
    @Autowired
    internal constructor(
        private val priceService: PriceService,
        private val fxRateService: FxService,
    ) {
        fun getMarketData(
            priceRequest: PriceRequest,
            token: String,
        ): PriceResponse {
            return priceService.getPrices(priceRequest, token)
        }

        fun getFxData(
            fxRequest: FxRequest,
            token: String,
        ): FxResponse {
            return fxRateService.getRates(fxRequest, token)
        }
    }
