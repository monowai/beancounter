package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import com.beancounter.marketdata.service.MarketDataProvider
import com.fasterxml.jackson.core.JsonProcessingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import javax.annotation.PostConstruct
import kotlin.collections.set

/**
 * Facade. AlphaAdvantage - www.alphavantage.co.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Service
class AlphaService(private val alphaConfig: AlphaConfig) : MarketDataProvider {
    private val dateUtils = DateUtils()

    @Value("\${beancounter.market.providers.ALPHA.key:demo}")
    private val apiKey: String? = null
    private lateinit var alphaProxyCache: AlphaProxyCache
    private lateinit var alphaPriceAdapter: AlphaPriceAdapter

    @Autowired
    fun setAlphaHelpers(alphaProxyCache: AlphaProxyCache, alphaPriceAdapter: AlphaPriceAdapter) {
        this.alphaProxyCache = alphaProxyCache
        this.alphaPriceAdapter = alphaPriceAdapter
    }

    @PostConstruct
    fun logStatus() {
        val isDemo = apiKey!!.substring(0, 4).equals("demo", ignoreCase = true)
        log.info("DEMO key is {}", isDemo)
    }

    private fun isCurrent(date: String): Boolean {
        return dateUtils.isToday(date)
    }

    override fun getMarketData(priceRequest: PriceRequest): Collection<MarketData> {
        val providerArguments = getInstance(priceRequest, alphaConfig)
        val requests: MutableMap<Int, Future<String?>?> = ConcurrentHashMap()
        for (batchId in providerArguments.batch.keys) {
            val date = providerArguments.getBatchConfigs()[batchId]!!.date
            if (isCurrent(priceRequest.date)) {
                requests[batchId] = alphaProxyCache.getCurrent(providerArguments.batch[batchId],
                        priceRequest.date, apiKey)
            } else {
                requests[batchId] = alphaProxyCache.getHistoric(providerArguments.batch[batchId], date, apiKey)
            }
        }
        return getMarketData(providerArguments, requests)
    }

    private fun getMarketData(providerArguments: ProviderArguments,
                              requests: MutableMap<Int, Future<String?>?>): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList()
        while (requests.isNotEmpty()) {
            for (batch in requests.keys) {
                if (requests[batch]!!.isDone) {
                    try {
                        results.addAll(
                                alphaPriceAdapter[providerArguments, batch, requests[batch]!!.get()])
                    } catch (e: InterruptedException) {
                        log.error(e.message)
                        throw SystemException("This shouldn't have happened")
                    } catch (e: ExecutionException) {
                        log.error(e.message)
                        throw SystemException("This shouldn't have happened")
                    }
                    requests.remove(batch)
                }
            }
        }
        return results
    }

    override fun getId(): String {
        return ID
    }

    override fun isMarketSupported(market: Market): Boolean {
        return if (alphaConfig.markets == null) {
            false
        } else alphaConfig.markets!!.contains(market.code)
    }

    override fun getDate(market: Market, priceRequest: PriceRequest): LocalDate {
        return alphaConfig.getMarketDate(market, priceRequest.date)
    }

    override fun backFill(asset: Asset): PriceResponse {
        val results = alphaProxyCache.getAdjusted(asset.code, apiKey)
        val json: String?
        json = try {
            results.get()
        } catch (e: InterruptedException) {
            log.error(e.message)
            throw SystemException("This shouldn't have happened")
        } catch (e: ExecutionException) {
            log.error(e.message)
            throw SystemException("This shouldn't have happened")
        }
        val priceResponse: PriceResponse?
        priceResponse = try {
            alphaPriceAdapter.alphaMapper.readValue(json, PriceResponse::class.java)
        } catch (e: JsonProcessingException) {
            log.error(e.message)
            throw SystemException("This shouldn't have happened")
        }
        if (priceResponse != null && priceResponse.data.isNotEmpty()) {
            for (marketData in priceResponse.data) {
                marketData.source = ID
                marketData.asset = asset
            }
            return priceResponse
        }
        return PriceResponse()
    }

    companion object {
        const val ID = "ALPHA"
        private val log = LoggerFactory.getLogger(AlphaService::class.java)
    }

}