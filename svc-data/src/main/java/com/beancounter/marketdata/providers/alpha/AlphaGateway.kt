package com.beancounter.marketdata.providers.alpha

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 * Api calls to alphavantage.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@FeignClient(name = "alphaVantage", url = "\${beancounter.market.providers.ALPHA.url:https://www.alphavantage.co}")
interface AlphaGateway {
    @RequestMapping(method = [RequestMethod.GET], headers = ["Content-Type: text/plain"], value = ["/query?function=GLOBAL_QUOTE&symbol={assetId}&apikey={apiKey}"])
    fun getCurrent(@PathVariable("assetId") assetId: String?,
                   @PathVariable("apiKey") apiKey: String?): String?

    @RequestMapping(method = [RequestMethod.GET], headers = ["Content-Type: text/plain"], value = ["/query?function=TIME_SERIES_DAILY&symbol={assetId}&apikey={apiKey}"])
    fun getHistoric(@PathVariable("assetId") assetId: String?,
                    @PathVariable("apiKey") apiKey: String?): String?

    @RequestMapping(method = [RequestMethod.GET], headers = ["Content-Type: text/plain"], value = ["/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol={assetId}&apikey={apiKey}"])
    fun getAdjusted(@PathVariable("assetId") assetId: String?,
                    @PathVariable("apiKey") apiKey: String?): String?

    @RequestMapping(method = [RequestMethod.GET], headers = ["Content-Type: text/plain"], value = ["/query?function=SYMBOL_SEARCH&keywords={symbol}&apikey={apiKey}"])
    fun search(@PathVariable("symbol") symbol: String?,
               @PathVariable("apiKey") apiKey: String?): String?
}