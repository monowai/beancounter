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
        var apiResults: Collection<MarketData> = ArrayList()
        var marketDate: LocalDate?
        val marketData: MutableMap<String, LocalDate> = mutableMapOf()

        for (marketDataProvider in byFactory.keys) {
            // Pull from the DB
            val assetIterable = (byFactory[marketDataProvider] ?: error("")).iterator()
            while (assetIterable.hasNext()) {
                val asset = assetIterable.next()
                marketDate = marketData[asset.market.timezone.id]
                if (marketDate == null) {
                    marketDate = marketDataProvider.getDate(asset.market, priceRequest)
                    if (priceRequest.assets.size > 1) {
                        marketData[asset.market.timezone.id] = marketDate
                        log.debug("Requested date ${priceRequest.date} resolved as $marketDate")
                    }
                }

                val md = priceService.getMarketData(asset.id, marketDate)
                if (md.isPresent) {
                    val mdValue = md.get()
                    mdValue.asset = asset
                    fromDb.add(mdValue)
                    assetIterable.remove() // One less external query to make
                }
            }

            // Pull the balance over external API integration
            val apiAssets = byFactory[marketDataProvider]
            if (!apiAssets!!.isEmpty()) {
                val assetInputs = providerUtils.getInputs(apiAssets)
                val apiRequest = PriceRequest(priceRequest.date, assetInputs)
                apiResults = marketDataProvider.getMarketData(apiRequest)
            }
        }
        // Merge results into a response
        if (fromDb.size + apiResults.size > 1) {
            log.debug("From DB: ${fromDb.size}, from API: ${apiResults.size}")
        }
        if (apiResults.isNotEmpty()) {
            priceService.write(PriceResponse(apiResults)) // Async write
        }
        fromDb.addAll(apiResults)
        return PriceResponse(fromDb)
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
