package com.beancounter.marketdata.markets

import com.beancounter.common.model.Market
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component

/**
 * Market Object Management Service.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.market")
@Import(CurrencyService::class)
@Component
class MarketConfig @Autowired constructor(
    val values: Collection<Market>,
    val currencyService: CurrencyService
) {

    fun getProviders(): Map<String, Market> {
        val providers: MutableMap<String, Market> = HashMap()
        for (market in values) {
            market.currency = currencyService.getCode(market.currencyId)
            providers[market.code] = market
        }
        return providers
    }
}
