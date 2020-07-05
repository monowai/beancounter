package com.beancounter.marketdata.providers.alpha

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import org.springframework.stereotype.Service
import java.util.concurrent.Future

/**
 * Async proxy to obtain MarketData from Alpha.
 *
 * @author mikeh
 * @since 2019-03-06
 */
@Service
class AlphaProxyCache {
    private var alphaProxy: AlphaProxy? = null

    @Autowired(required = false)
    fun setAlphaProxy(alphaProxy: AlphaProxy?) {
        this.alphaProxy = alphaProxy
    }

    @Cacheable("asset.prices")
    @Async
    fun getCurrent(code: String?,
                   date: String?,
                   apiKey: String?): Future<String?> {
        return if (code == null) {
            AsyncResult(null)
        } else AsyncResult(alphaProxy!!.getCurrent(code, apiKey))
    }

    @Async
    fun getHistoric(code: String?, date: String?, apiKey: String?): Future<String?> {
        return AsyncResult(alphaProxy!!.getHistoric(code, apiKey))
    }

    @Cacheable("asset.search")
    @Async
    fun search(symbol: String?, apiKey: String?): Future<String?> {
        return AsyncResult(alphaProxy!!.search(symbol, apiKey))
    }

    fun getAdjusted(code: String?, apiKey: String?): Future<String?> {
        return AsyncResult(alphaProxy!!.adjusted(code, apiKey))
    }
}