package com.beancounter.marketdata.fx.fxrates

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 * Api calls to exchangeratesapi.io.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@FeignClient(
    name = "fxRequest",
    url = "\${beancounter.market.providers.fx.url:https://api.exchangeratesapi.io}",
)
interface FxGateway {
    @RequestMapping(
        method = [RequestMethod.GET],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        value = ["/v1/{date}?base={base}&symbols={symbols}&access_key=\${beancounter.market.providers.FX.key}"],
    )
    fun getRatesForSymbols(
        @PathVariable("date") date: String,
        @PathVariable("base") base: String,
        @PathVariable("symbols") symbols: String,
    ): ExRatesResponse?
}
