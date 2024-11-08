package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
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
class MarketDataService
    @Autowired
    internal constructor(
        private val providerUtils: ProviderUtils,
        private val priceService: PriceService,
    ) {
        private val log = LoggerFactory.getLogger(MarketDataService::class.java)

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
            val byProviders = providerUtils.splitProviders(priceRequest.assets)
            val foundInDb: MutableCollection<MarketData> = mutableListOf()
            val foundOverApi: MutableCollection<MarketData> = mutableListOf()
            val customPrice: MutableCollection<MarketData> = mutableListOf()
            val cachedDates: MutableMap<String, LocalDate> = mutableMapOf()

            for (marketDataProvider in byProviders.keys) {
                val assets = (byProviders[marketDataProvider] ?: error("")).iterator()
                while (assets.hasNext()) {
                    val asset = assets.next()
                    val market = asset.market
                    if (!cachedDates.containsKey(market.timezone.id)) {
                        val marketDate = getMarketDate(marketDataProvider, asset, priceRequest)
                        cachedDates[market.timezone.id] = marketDate
                    }

                    val marketData = getMarketData(asset, priceRequest, cachedDates[market.timezone.id]!!)
                    if (marketData.isPresent) {
                        val mdValue = marketData.get()
                        mdValue.asset = asset
                        foundInDb.add(mdValue)
                        assets.remove()
                    }
                }
                val results = getFromProvider(byProviders[marketDataProvider], cachedDates, marketDataProvider)
                if (marketDataProvider.isApiSupported()) {
                    foundOverApi.addAll(results)
                } else {
                    customPrice.addAll(results)
                }
            }

            if (foundInDb.size + foundOverApi.size > 1) {
                log.debug("From DB: ${foundInDb.size}, from API: ${foundOverApi.size}, custom prices: ${customPrice.size}")
            }
            if (foundOverApi.isNotEmpty()) {
                priceService.write(PriceResponse(foundOverApi))
            }
            foundInDb.addAll(foundOverApi)
            foundInDb.addAll(customPrice)
            return PriceResponse(foundInDb)
        }

        private fun getMarketData(
            asset: Asset,
            priceRequest: PriceRequest,
            marketDate: LocalDate,
        ): Optional<MarketData> = priceService.getMarketData(asset, marketDate, priceRequest.closePrice)

        private fun getFromProvider(
            providerAssets: MutableCollection<Asset>?,
            timezonePriceDates: MutableMap<String, LocalDate>,
            marketDataPriceProvider: MarketDataPriceProvider,
        ): Collection<MarketData> {
            if (!providerAssets!!.isEmpty()) {
                val assetInputs = providerUtils.getInputs(providerAssets)
                val priceDate =
                    timezonePriceDates[
                        providerAssets
                            .iterator()
                            .next()
                            .market.timezone.id,
                    ]
                val priceRequest = PriceRequest(priceDate.toString(), assetInputs)
                return marketDataPriceProvider.getMarketData(priceRequest)
            }
            return arrayListOf()
        }

        fun getMarketDate(
            marketDataPriceProvider: MarketDataPriceProvider,
            asset: Asset,
            priceRequest: PriceRequest,
        ): LocalDate {
            val marketDate = marketDataPriceProvider.getDate(asset.market, priceRequest)
            if (!CashUtils().isCash(asset)) {
                log.debug("Requested date: ${priceRequest.date}, resolvedDate: $marketDate")
            }
            return marketDate
        }

        /**
         * Delete all prices.  Supports testing
         */
        fun purge() {
            priceService.purge()
        }

        fun refresh(
            asset: Asset,
            priceDate: String,
        ) {
            val priceAssets = mutableListOf(PriceAsset(asset))
            log.trace("Refreshing ${asset.name}: $priceDate")
            val providers = providerUtils.splitProviders(priceAssets)
            val priceRequest = PriceRequest(date = priceDate, assets = priceAssets)
            val marketDate = getMarketDate(providers.keys.iterator().next(), asset, priceRequest)
            val response =
                getMarketData(
                    asset,
                    priceRequest = priceRequest,
                    marketDate = marketDate,
                )
            if (response.isPresent) {
                priceService.purge(response.get())
            }
        }
    }
