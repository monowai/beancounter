package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Service container for MarketData information.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Service
class MarketDataService @Autowired internal constructor(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService,
) {
    @Transactional
    fun backFill(asset: Asset) {
        val byFactory = providerUtils.splitProviders(providerUtils.getInputs(mutableListOf(asset)))
        for (marketDataProvider in byFactory.keys) {
            priceService.process(marketDataProvider.backFill(asset))
        }
    }

    /**
     * Prices for the request.
     *
     * @param priceRequest to process
     * @return results
     */
    @Transactional
    fun getPriceResponse(priceRequest: PriceRequest): PriceResponse {
        val byFactory = providerUtils.splitProviders(priceRequest.assets)
        val fromDb: MutableCollection<MarketData> = ArrayList()
        var fromApi: Collection<MarketData> = ArrayList()
        var marketDate: LocalDate?
        val marketData: MutableMap<String, LocalDate> = mutableMapOf()

        for (marketDataProvider in byFactory.keys) {
            // Pull from the DB
            val assetIterable = (byFactory[marketDataProvider] ?: error("")).iterator()
            while (assetIterable.hasNext()) {
                val asset = assetIterable.next()
                marketDate = getMarketDate(
                    marketData[asset.market.timezone.id],
                    marketDataProvider,
                    asset,
                    priceRequest,
                    marketData
                )

                val md = priceService.getMarketData(asset.id, marketDate)
                if (md.isPresent) {
                    val mdValue = md.get()
                    mdValue.asset = asset
                    fromDb.add(mdValue)
                    assetIterable.remove() // One less external query to make
                }
            }

            // Pull the balance over external API integration
            fromApi = getExternally(byFactory[marketDataProvider], priceRequest, fromApi, marketDataProvider)
        }
        // Merge results into a response
        if (fromDb.size + fromApi.size > 1) {
            log.debug("From DB: ${fromDb.size}, from API: ${fromApi.size}")
        }
        if (fromApi.isNotEmpty()) {
            priceService.write(PriceResponse(fromApi)) // Async write
        }
        fromDb.addAll(fromApi)
        return PriceResponse(fromDb)
    }

    private fun getExternally(
        apiAssets: MutableCollection<Asset>?,
        priceRequest: PriceRequest,
        apiResults: Collection<MarketData>,
        marketDataProvider: MarketDataProvider
    ): Collection<MarketData> {
        var apiResults1 = apiResults
        if (!apiAssets!!.isEmpty()) {
            val assetInputs = providerUtils.getInputs(apiAssets)
            val apiRequest = PriceRequest(priceRequest.date, assetInputs)
            apiResults1 = marketDataProvider.getMarketData(apiRequest)
        }
        return apiResults1
    }

    private fun getMarketDate(
        forTimezone: LocalDate?,
        marketDataProvider: MarketDataProvider,
        asset: Asset,
        priceRequest: PriceRequest,
        marketData: MutableMap<String, LocalDate>
    ): LocalDate {
        var marketDate = forTimezone
        if (marketDate == null) {
            marketDate = marketDataProvider.getDate(asset.market, priceRequest)
            if (priceRequest.assets.size > 1) {
                marketData[asset.market.timezone.id] = marketDate
                log.debug("Requested date ${priceRequest.date} resolved as $marketDate")
            }
        }
        return marketDate
    }

    /**
     * Delete all prices.  Supports testing
     */
    fun purge() {
        priceService.purge()
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
