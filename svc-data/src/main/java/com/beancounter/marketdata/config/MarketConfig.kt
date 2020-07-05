package com.beancounter.marketdata.config

import com.beancounter.common.model.Market
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.util.*
import javax.annotation.PostConstruct

/**
 * Static data configuration.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.market")
@Component
class MarketConfig {
    var values: Collection<Market>? = null
    private var providers: MutableMap<String, Market> = HashMap()

    @set:Autowired
    var currencyService: CurrencyService? = null
    var marketService: MarketService? = null

    /**
     * Convert from configured representation to Objects.
     */
    @PostConstruct
    fun configure() {
        handleMarkets()
    }

    private fun handleMarkets() {
        for (market in values!!) {
            market.currency = currencyService!!.getCode(market.currencyId)!!
            providers[market.code] = market
        }
    }

    fun getProviders(): Map<String, Market> {
        return providers
    }

    fun setProviders(providers: MutableMap<String, Market>) {
        this.providers = providers
    }

}