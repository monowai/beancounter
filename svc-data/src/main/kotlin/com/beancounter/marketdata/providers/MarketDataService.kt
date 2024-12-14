package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.providers.cash.CashProviderService.Companion.ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Optional

/**
 * Service container for obtaining MarketData information from a provider.
 *
 */
@Import(ProviderUtils::class)
@Service
class MarketDataService
    @Autowired
    constructor(
        private val providerUtils: ProviderUtils,
        private val priceService: PriceService
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
            val foundInDb: MutableList<MarketData> = mutableListOf()
            val foundOverApi: MutableList<MarketData> = mutableListOf()
            val customPrice: MutableList<MarketData> = mutableListOf()
            val cachedDates: MutableMap<String, LocalDate> = mutableMapOf()
            for (marketDataProvider in byProviders.keys) {
                val assets = (byProviders[marketDataProvider] ?: error(""))
                log.debug("marketDataProvider: ${marketDataProvider.getId()}, assets: ${assets.size}")
                buildDateCache(
                    cachedDates,
                    marketDataProvider,
                    assets,
                    priceRequest
                )
                val priceDate =
                    cachedDates[
                        assets
                            .iterator()
                            .next()
                            .market.timezone.id
                    ]!!
                val existingPrices =
                    if (marketDataProvider.getId() == ID) {
                        // Cash is constant
                        getFromProvider(
                            byProviders[marketDataProvider],
                            priceDate,
                            marketDataProvider,
                            priceRequest
                        )
                    } else {
                        priceService.getMarketData(
                            assets,
                            priceDate
                        )
                    }
                val assetsIterator = assets.iterator()
                while (assetsIterator.hasNext()) {
                    val asset = assetsIterator.next()
                    val existingPrice = existingPrices.find { it.asset.id == asset.id }
                    if (existingPrice != null) {
                        existingPrice.asset = asset
                        foundInDb.add(existingPrice)
                        assetsIterator.remove()
                    }
                }
                val results =
                    getFromProvider(
                        byProviders[marketDataProvider],
                        priceDate,
                        marketDataProvider,
                        priceRequest
                    )
                if (marketDataProvider.isApiSupported()) {
                    foundOverApi.addAll(results)
                } else {
                    customPrice.addAll(results)
                }
            }

            if (foundInDb.size + foundOverApi.size > 1) {
                log.debug(
                    "From DB: ${foundInDb.size}, from API: ${foundOverApi.size}, custom prices: ${customPrice.size}"
                )
            }
            if (foundOverApi.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    priceService.handle(PriceResponse(foundOverApi))
                }
            }
            foundInDb.addAll(foundOverApi)
            foundInDb.addAll(customPrice)
            return PriceResponse(foundInDb)
        }

        private fun buildDateCache(
            cachedDates: MutableMap<String, LocalDate>,
            marketDataProvider: MarketDataPriceProvider,
            assets: Collection<Asset>,
            priceRequest: PriceRequest
        ) {
            assets.forEach { asset ->
                cachedDates.computeIfAbsent(asset.market.timezone.id) {
                    getMarketDate(
                        marketDataProvider,
                        asset,
                        priceRequest
                    )
                }
            }
        }

        private fun getMarketData(
            asset: Asset,
            priceRequest: PriceRequest,
            marketDate: LocalDate
        ): Optional<MarketData> =
            priceService.getMarketData(
                asset,
                marketDate,
                priceRequest.closePrice
            )

        private fun getFromProvider(
            providerAssets: MutableCollection<Asset>?,
            priceDate: LocalDate,
            marketDataPriceProvider: MarketDataPriceProvider,
            request: PriceRequest
        ): Collection<MarketData> {
            if (!providerAssets!!.isEmpty()) {
                val assetInputs = providerUtils.getInputs(providerAssets)
                val priceRequest =
                    PriceRequest(
                        priceDate.toString(),
                        assets = assetInputs,
                        currentMode = request.currentMode,
                        closePrice = request.closePrice
                    )
                return marketDataPriceProvider.getMarketData(priceRequest)
            }
            return listOf()
        }

        fun getMarketDate(
            marketDataPriceProvider: MarketDataPriceProvider,
            asset: Asset,
            priceRequest: PriceRequest
        ): LocalDate {
            val marketDate =
                marketDataPriceProvider.getDate(
                    asset.market,
                    priceRequest
                )
            if (!CashUtils().isCash(asset)) {
                log.trace("Requested date: ${priceRequest.date}, resolvedDate: $marketDate")
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
            priceDate: String
        ) {
            val priceAssets = mutableListOf(PriceAsset(asset))
            log.trace("Refreshing ${asset.name}: $priceDate")
            val providers = providerUtils.splitProviders(priceAssets)
            val priceRequest =
                PriceRequest(
                    date = priceDate,
                    assets = priceAssets
                )
            val marketDate =
                getMarketDate(
                    providers.keys.iterator().next(),
                    asset,
                    priceRequest
                )
            val response =
                getMarketData(
                    asset,
                    priceRequest = priceRequest,
                    marketDate = marketDate
                )
            if (response.isPresent) {
                priceService.purge(response.get())
            }
        }
    }