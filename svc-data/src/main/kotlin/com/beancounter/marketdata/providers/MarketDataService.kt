package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Service container for MarketData information.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Import(ProviderUtils::class)
@Service
class MarketDataService @Autowired internal constructor(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService,
) {
    @Transactional
    fun backFill(asset: Asset) {
        val byFactory = providerUtils.splitProviders(providerUtils.getInputs(mutableListOf(asset)))
        for (marketDataProvider in byFactory.keys) {
            priceService.handle(marketDataProvider.backFill(asset))
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
        val foundInDb: MutableCollection<MarketData> = mutableListOf()
        val foundOverApi: MutableCollection<MarketData> = mutableListOf()
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
                    marketData,
                )

                val md = priceService.getMarketData(asset, marketDate, priceRequest.closePrice)
                if (md.isPresent) {
                    val mdValue = md.get()
                    mdValue.asset = asset
                    foundInDb.add(mdValue)
                    assetIterable.remove() // One less external query to make
                }
            }

            // Pull the balance over external API integration
            foundOverApi.addAll(getExternally(byFactory[marketDataProvider], priceRequest.date, marketDataProvider))
        }
        // Merge results into a response
        if (foundInDb.size + foundOverApi.size > 1) {
            log.debug("From DB: ${foundInDb.size}, from API: ${foundOverApi.size}")
        }
        if (foundOverApi.isNotEmpty()) {
            priceService.write(PriceResponse(foundOverApi)) // Async write
        }
        foundInDb.addAll(foundOverApi)
        return PriceResponse(foundInDb)
    }

    private fun getExternally(
        apiAssets: MutableCollection<Asset>?,
        date: String,
        marketDataPriceProvider: MarketDataPriceProvider,
    ): Collection<MarketData> {
        if (!apiAssets!!.isEmpty()) {
            val assetInputs = providerUtils.getInputs(apiAssets)
            val apiRequest = PriceRequest(date, assetInputs)
            return marketDataPriceProvider.getMarketData(apiRequest)
        }
        return arrayListOf()
    }

    private fun getMarketDate(
        forTimezone: LocalDate?,
        marketDataPriceProvider: MarketDataPriceProvider,
        asset: Asset,
        priceRequest: PriceRequest,
        marketData: MutableMap<String, LocalDate>,
    ): LocalDate {
        var marketDate = forTimezone
        if (marketDate == null) {
            marketDate = marketDataPriceProvider.getDate(asset.market, priceRequest)
            if (priceRequest.assets.size > 1) {
                marketData[asset.market.timezone.id] = marketDate
                log.debug("Requested date: ${priceRequest.date}, resolvedDate: $marketDate, asset: ${asset.name}, assetId: ${asset.id}")
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
