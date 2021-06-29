package com.beancounter.marketdata.providers.wtd

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import org.springframework.stereotype.Service
import java.util.concurrent.Future

/**
 * Async proxy to obtain MarketData.
 *
 * @author mikeh
 * @since 2019-03-06
 */
@Service
class WtdProxy {
    private lateinit var wtdGateway: WtdGateway

    @Autowired(required = false)
    fun setWtdGateway(wtdGateway: WtdGateway) {
        this.wtdGateway = wtdGateway
    }

    @Async("priceExecutor")
    @Cacheable("asset.prices")
    fun getPrices(assets: String?, marketOpenDate: String?, apiKey: String?): Future<WtdResponse> {
        val result = wtdGateway.getPrices(assets, marketOpenDate, apiKey)
        return AsyncResult(result)
    }
}
