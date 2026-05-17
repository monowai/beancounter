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
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Facade for MarketStack.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Service
class MarketStackService(
    private val marketStackProxy: MarketStackProxy,
    private val marketStackConfig: MarketStackConfig,
    private val marketStackAdapter: MarketStackAdapter,
    private val marketStackGateway: MarketStackGateway,
    private val dateUtils: DateUtils
) : MarketDataPriceProvider {
    private val log = LoggerFactory.getLogger(MarketStackService::class.java)

    @PostConstruct
    fun logStatus() {
        log.info(
            "BEANCOUNTER_MARKET_PROVIDERS_MSTACK_KEY: {}",
            if (marketStackConfig.apiKey.equals(
                    "DEMO",
                    ignoreCase = true
                )
            ) {
                "DEMO"
            } else {
                "** Redacted **"
            }
        )
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val apiRequests: MutableMap<Int, MarketStackResponse> = mutableMapOf()
        val providerArguments =
            getInstance(
                priceRequest,
                marketStackConfig,
                dateUtils
            )
        for (batch in providerArguments.batch.keys) {
            apiRequests[batch] =
                marketStackProxy.getPrices(
                    providerArguments,
                    batch,
                    marketStackConfig.apiKey
                )
        }
        log.trace("Assets price processing complete.")
        return getMarketData(
            providerArguments,
            apiRequests
        )
    }

    private fun getMarketData(
        providerArguments: ProviderArguments,
        marketStackResponses: MutableMap<Int, MarketStackResponse>
    ): Collection<MarketData> {
        val results: MutableCollection<MarketData> = mutableListOf()
        for (key in marketStackResponses.keys) {
            marketStackResponses[key]?.let {
                val x =
                    marketStackAdapter.toMarketData(
                        providerArguments,
                        key,
                        it
                    )
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

    /**
         * Resolve the effective date to use for price retrieval for a specific market and request.
         *
         * @param market The market for which the date is being resolved.
         * @param priceRequest The price request containing the requested date or date context.
         * @return The LocalDate to use when requesting prices for the specified market.
         */
        override fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate =
        marketStackConfig.getMarketDate(
            market,
            priceRequest.date
        )

    /**
     * Fetches historical price data for an asset starting from the given date and returns it as a PriceResponse.
     *
     * @param asset The asset to backfill prices for.
     * @param fromDate The start date (inclusive) for the historical data retrieval.
     * @return A PriceResponse containing market data for the asset from `fromDate` up to today.
     */
    override fun backFill(
        asset: Asset,
        fromDate: LocalDate
    ): PriceResponse {
        val symbol = marketStackConfig.getPriceCode(asset)
        val dateTo = dateUtils.today()
        val response =
            marketStackGateway.getHistory(
                symbol,
                fromDate.toString(),
                dateTo,
                marketStackConfig.apiKey
            )
        return PriceResponse(marketStackAdapter.toMarketData(asset, response))
    }

    override fun isApiSupported(): Boolean = true

    /**
     * MarketStack Service constants.
     */
    companion object {
        const val ID = "MSTACK"
    }
}