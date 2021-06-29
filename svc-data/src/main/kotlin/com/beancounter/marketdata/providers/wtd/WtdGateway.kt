package com.beancounter.marketdata.providers.wtd

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
@FeignClient(name = "worldTradingData", url = "\${beancounter.market.providers.WTD.url:https://www.worldtradingdata.com}")
interface WtdGateway {
    @RequestMapping(method = [RequestMethod.GET], value = ["/api/v1/history_multi_single_day?symbol={assets}&date={date}&api_token={apiKey}"])
    fun getPrices(
        @PathVariable("assets") assetId: String?,
        @PathVariable("date") date: String?,
        @PathVariable("apiKey") apiKey: String?
    ): WtdResponse?
}
