package com.beancounter.position.service

import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

/**
 * Facade to support async requests.
 */
@Service
class AsyncMdService @Autowired internal constructor(
    private val priceService: PriceService,
    private val fxRateService: FxService,
) {
    @Async
    @Retry(name = "bcData")
    fun getMarketData(priceRequest: PriceRequest): CompletableFuture<PriceResponse> {
        return CompletableFuture.completedFuture(priceService.getPrices(priceRequest))
    }

    @Async
    fun getFxData(fxRequest: FxRequest): CompletableFuture<FxResponse> {
        return CompletableFuture.completedFuture(fxRateService.getRates(fxRequest))
    }
}
