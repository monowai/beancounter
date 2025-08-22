package com.beancounter.marketdata.markets

import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Obtain Markets.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Service
@Import(MarketConfig::class)
@Transactional
class MarketService
    @Autowired
    constructor(
        val marketConfig: MarketConfig
    ) : com.beancounter.client.MarketService {
        private val aliases: MutableMap<String, String> = mutableMapOf()
        private var marketMap: Map<String, Market> = mutableMapOf()

        fun getMarketMap(): Map<String, Market> {
            if (marketMap.isEmpty()) {
                marketMap = marketConfig.getProviders()
                for (marketCode in marketMap.keys) {
                    val market = marketMap[marketCode]
                    setAlias(
                        market,
                        marketCode
                    )
                }
            }
            return marketMap
        }

        private fun setAlias(
            market: Market?,
            marketCode: String
        ) {
            if (market != null && market.aliases.isNotEmpty()) {
                for (provider in market.aliases.keys) {
                    aliases[market.aliases.getValue(provider).uppercase(Locale.getDefault())] =
                        marketCode.uppercase(Locale.getDefault())
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
            val alias = aliases[input.uppercase(Locale.getDefault())]
            return alias ?: input
        }

        /**
         * Resolves a market via its code property.
         *
         * @param marketCode non-null market code - can also be an alias
         * @return resolved market
         */
        override fun getMarket(marketCode: String): Market =
            getMarket(
                marketCode,
                true
            )

        fun getMarket(
            marketCode: String?,
            orByAlias: Boolean
        ): Market {
            if (marketCode == null) {
                throw BusinessException("Null Market Code")
            }
            var market = getMarketMap()[marketCode.uppercase(Locale.getDefault())]
            val errorMessage =
                String.format(
                    Locale.US,
                    "Unable to resolve market code %s",
                    marketCode
                )
            if (market == null && orByAlias) {
                val byAlias = resolveAlias(marketCode)
                market = marketMap[byAlias]
            }
            if (market == null) {
                throw BusinessException(errorMessage)
            }
            return market
        }

        override fun getMarkets(): MarketResponse = MarketResponse(getMarketMap().values)

        fun canPersist(market: Market): Boolean {
            // Don't persist Mock market assets
            return market.code != "MOCK"
        }
    }