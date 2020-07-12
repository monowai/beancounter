package com.beancounter.marketdata.markets

import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.marketdata.config.MarketConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.util.*

/**
 * Verification of Market related functions.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Service
@Import(MarketConfig::class)
class MarketService : com.beancounter.client.MarketService {
    private val aliases: MutableMap<String, String> = HashMap()
    private var marketMap: Map<String, Market> = HashMap()

    @Autowired
    fun setMarketConfig(marketConfig: MarketConfig) {
        marketMap = marketConfig.getProviders()
        for (marketCode in marketMap.keys) {
            val market = marketMap[marketCode]
            if (market != null) {
                if (market.aliases.isNotEmpty()) {
                    for (provider in market.aliases.keys) {
                        aliases[market.aliases.getValue(provider).toUpperCase()] = marketCode.toUpperCase()
                    }
                }
            }
        }
    }

    /**
     * Return the Exchange code to use for the supplied input.
     *
     * @param input code that *might* have an alias.
     * @return the alias or input if no exception is defined.
     */
    fun resolveAlias(input: String): String {
        val alias = aliases[input.toUpperCase()]
        return alias ?: input
    }

    /**
     * Resolves a market via its code property.
     *
     * @param marketCode non-null market code - can also be an alias
     * @return resolved market
     */
    override fun getMarket(marketCode: String): Market {
        return getMarket(marketCode, true)
    }

    fun getMarket(marketCode: String?, orByAlias: Boolean): Market {
        if (marketCode == null) {
            throw BusinessException("Null Market Code")
        }
        var market = marketMap[marketCode.toUpperCase()]
        val errorMessage = String.format("Unable to resolve market code %s", marketCode)
        if (market == null && orByAlias) {
            val byAlias = resolveAlias(marketCode)
            market = marketMap[byAlias]
        }
        if (market == null) {
            throw BusinessException(errorMessage)
        }
        return market
    }

    override fun getMarkets(): MarketResponse {
        return MarketResponse(marketMap.values)
    }

    fun canPersist(market: Market): Boolean {
        // Don't persist Mock market assets
        return !market.inMemory()
    }

}