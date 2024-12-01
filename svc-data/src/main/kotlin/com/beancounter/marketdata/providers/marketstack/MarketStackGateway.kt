package com.beancounter.marketdata.providers.marketstack

import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
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
@FeignClient(
    name = "marketstack",
    url = "\${beancounter.market.providers.mstack.url:https://api.marketstack.com}",
)
interface MarketStackGateway {
    @RequestMapping(
        method = [RequestMethod.GET],
        value = ["/v1/eod/{date}?symbols={assets}&access_key={apiKey}"],
    )
    fun getPrices(
        @PathVariable("assets") assetId: String,
        @PathVariable("date") date: String,
        @PathVariable("apiKey") apiKey: String = "demo",
    ): MarketStackResponse
}
