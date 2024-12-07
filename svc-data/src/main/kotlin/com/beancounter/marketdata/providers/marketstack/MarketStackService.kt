package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Facade for MarketStack.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Service
class MarketStackService
    @Autowired
    internal constructor(
        private val marketStackProxy: MarketStackProxy,
        private val marketStackConfig: MarketStackConfig,
        private val marketStackAdapter: MarketStackAdapter,
        private val dateUtils: DateUtils,
    ) : MarketDataPriceProvider {
        private val log = LoggerFactory.getLogger(MarketStackService::class.java)

        @PostConstruct
        fun logStatus() {
            log.info(
                "BEANCOUNTER_MARKET_PROVIDERS_MSTACK_KEY: {}",
                if (marketStackConfig.apiKey.equals("DEMO", ignoreCase = true)) "DEMO" else "** Redacted **",
            )
        }

        override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
            val apiRequests: MutableMap<Int, MarketStackResponse> = mutableMapOf()
            val providerArguments = getInstance(priceRequest, marketStackConfig, dateUtils)
            for (batch in providerArguments.batch.keys) {
                apiRequests[batch] =
                    marketStackProxy.getPrices(
                        providerArguments,
                        batch,
                        marketStackConfig.apiKey,
                    )
            }
            log.trace("Assets price processing complete.")
            return getMarketData(providerArguments, apiRequests)
        }

        private fun getMarketData(
            providerArguments: ProviderArguments,
            marketStackResponses: MutableMap<Int, MarketStackResponse>,
        ): Collection<MarketData> {
            val results: MutableCollection<MarketData> = mutableListOf()
            for (key in marketStackResponses.keys) {
                marketStackResponses[key]?.let {
                    val x = marketStackAdapter.toMarketData(providerArguments, key, it)
                    results.addAll(x)
                }
            }
            return results
        }

        override fun getId(): String = ID

        override fun isMarketSupported(market: Market): Boolean =
            if (marketStackConfig.markets!!.isBlank()) {
            false
            } else {
                marketStackConfig.markets!!.contains(market.code)
        }

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest,
    ): LocalDate = marketStackConfig.getMarketDate(market, priceRequest.date)

    override fun backFill(asset: Asset): PriceResponse = PriceResponse()

    override fun isApiSupported(): Boolean = true

    /**
     * MarketStack Service constants.
     */
    companion object {
        const val ID = "MSTACK"
    }
}
