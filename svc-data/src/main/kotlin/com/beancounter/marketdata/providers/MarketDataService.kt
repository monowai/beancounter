package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
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
import java.util.Optional

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
        val marketData: MutableMap<String, LocalDate> = mutableMapOf()

        for (marketDataProvider in byFactory.keys) {
            // Find existing price for date
            val assetIterable = (byFactory[marketDataProvider] ?: error("")).iterator()
            while (assetIterable.hasNext()) {
                getMarketData(assetIterable, marketDataProvider, priceRequest, marketData, foundInDb)
            }

            // Pull the balance over external API integration
            foundOverApi.addAll(
                getExternally(byFactory[marketDataProvider], priceRequest.date, marketDataProvider),
            )
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

    private fun getMarketData(
        assetIterable: MutableIterator<Asset>,
        marketDataProvider: MarketDataPriceProvider,
        priceRequest: PriceRequest,
        marketDates: MutableMap<String, LocalDate> = mutableMapOf(),
        foundInDb: MutableCollection<MarketData> = mutableListOf(),
    ): Optional<MarketData> {
        val asset = assetIterable.next()
        val marketDate = getMarketDate(
            marketDataProvider,
            asset,
            priceRequest,
            marketDates,
        )

        val md = priceService.getMarketData(asset, marketDate, priceRequest.closePrice)
        if (md.isPresent) {
            val mdValue = md.get()
            mdValue.asset = asset
            foundInDb.add(mdValue)
            assetIterable.remove() // One less external query to make
        }
        return md
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
        marketDataPriceProvider: MarketDataPriceProvider,
        asset: Asset,
        priceRequest: PriceRequest,
        marketDates: MutableMap<String, LocalDate>,
    ): LocalDate {
        val forTimezone = marketDates[asset.market.timezone.id]
        if (forTimezone == null) {
            val marketDate = marketDataPriceProvider.getDate(asset.market, priceRequest)
            marketDates[asset.market.timezone.id] = marketDate
            if (priceRequest.assets.size > 1) {
                log.debug("Requested date: ${priceRequest.date}, resolvedDate: $marketDate, asset: ${asset.name}, assetId: ${asset.id}")
            }
            return marketDate
        }
        return forTimezone
    }

    /**
     * Delete all prices.  Supports testing
     */
    fun purge() {
        priceService.purge()
    }

    fun refresh(asset: Asset, priceDate: String) {
        val priceAssets = mutableListOf(PriceAsset(asset))
        val priceRequest = PriceRequest(date = priceDate, assets = priceAssets)
        val providers = providerUtils.splitProviders(priceAssets)
        val dateMap = mutableMapOf<String, LocalDate>()
        val md = getMarketData(
            mutableListOf(asset).iterator(),
            marketDataProvider = providers.keys.iterator().next(),
            priceRequest = priceRequest,
            marketDates = dateMap,
        )
        if (md.isPresent) {
            priceService.purge(md.get())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
