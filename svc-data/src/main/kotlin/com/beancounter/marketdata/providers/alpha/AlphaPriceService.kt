package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.collections.set

/**
 * Facade. AlphaAdvantage - www.alphavantage.co.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Service
class AlphaPriceService(private val alphaConfig: AlphaConfig) :
    MarketDataPriceProvider {
    @Value("\${beancounter.market.providers.ALPHA.key:demo}")
    private lateinit var apiKey: String
    private lateinit var alphaProxy: AlphaProxy
    private lateinit var alphaPriceAdapter: AlphaPriceAdapter

    @Autowired
    fun setAlphaHelpers(
        alphaProxyCache: AlphaProxy,
        alphaPriceAdapter: AlphaPriceAdapter,
    ) {
        this.alphaProxy = alphaProxyCache
        this.alphaPriceAdapter = alphaPriceAdapter
    }

    @PostConstruct
    fun logStatus() =
        log.info(
            "BEANCOUNTER_MARKET_PROVIDERS_ALPHA_KEY: {}",
            if (apiKey.substring(0, 4).equals("demo", ignoreCase = true)) "demo" else "** Redacted **",
        )

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val providerArguments = getInstance(priceRequest, alphaConfig)
        val requests = mutableMapOf<Int, Deferred<String>>()

        for (batchId in providerArguments.batch.keys) {
            requests[batchId] =
                CoroutineScope(Dispatchers.Default).async {
                    if (priceRequest.currentMode) {
                        alphaProxy.getCurrent(providerArguments.batch[batchId]!!, apiKey)
                    } else {
                        alphaProxy.getHistoric(providerArguments.batch[batchId]!!, apiKey)
                    }
                }
        }

        return runBlocking { getMarketData(providerArguments, requests) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getMarketData(
        providerArguments: ProviderArguments,
        requests: MutableMap<Int, Deferred<String>>,
    ): Collection<MarketData> {
        val results = mutableListOf<MarketData>()

        while (requests.isNotEmpty()) {
            val completedBatches =
                requests.filter { (_, requestDeferred) ->
                    requestDeferred.isCompleted
                }

            completedBatches.forEach { (batchId, requestDeferred) ->
                results.addAll(
                    alphaPriceAdapter[providerArguments, batchId, requestDeferred.getCompleted()],
                )

                log.trace("Processed batch ${requests.remove(batchId)}")
            }
        }

        return results
    }

    override fun getId(): String {
        return ID
    }

    override fun isMarketSupported(market: Market) =
        if (alphaConfig.markets == null) {
            false
        } else {
            alphaConfig.markets!!.contains(market.code)
        }

    override fun getDate(
        market: Market,
        priceRequest: PriceRequest,
    ) = alphaConfig.getMarketDate(market, priceRequest.date, priceRequest.currentMode)

    override fun backFill(asset: Asset): PriceResponse {
        val json = alphaProxy.getAdjusted(asset.code, apiKey)
        val priceResponse: PriceResponse =
            alphaConfig.getObjectMapper()
                .readValue(json, PriceResponse::class.java)
        for (marketData in priceResponse.data) {
            marketData.source = ID
            marketData.asset = asset
        }
        return priceResponse
    }

    companion object {
        const val ID = "ALPHA"
        private val log = LoggerFactory.getLogger(AlphaPriceService::class.java)
    }
}
