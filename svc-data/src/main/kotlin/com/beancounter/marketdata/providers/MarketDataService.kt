package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.cash.CashProviderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Service container for obtaining MarketData information from a provider.
 *
 */
@Import(ProviderUtils::class)
@Service
@Transactional
class MarketDataService(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService,
    private val assetService: AssetService
) {
    private val log = LoggerFactory.getLogger(MarketDataService::class.java)

    fun backFill(assetId: String) {
        backFill(assetService.find(assetId))
    }

    fun backFill(asset: Asset) {
        val byFactory = providerUtils.splitProviders(providerUtils.getInputs(listOf(asset)))
        for (marketDataProvider in byFactory.keys) {
            priceService.handle(marketDataProvider.backFill(asset))
        }
    }

    fun getPriceResponse(
        market: String,
        assetCode: String
    ): PriceResponse {
        val asset =
            assetService.findLocally(AssetInput(market, assetCode))
        return getPriceResponse(PriceRequest(assets = listOf(PriceAsset(asset))))
    }

    fun getPriceResponse(assetId: String): PriceResponse {
        val asset = assetService.find(assetId)
        return getPriceResponse(PriceRequest(assets = listOf(PriceAsset(asset))))
    }

    @Transactional(readOnly = true)
    fun getAssetPrices(priceRequest: PriceRequest): PriceResponse {
        val withResolvedAssets = assetService.resolveAssets(priceRequest)
        return getPriceResponse(withResolvedAssets)
    }

    /**
     * Prices for the request.
     *
     * @param priceRequest to process
     * @return results
     */
    fun getPriceResponse(priceRequest: PriceRequest): PriceResponse {
        val byProviders = providerUtils.splitProviders(priceRequest.assets)

        val priceResults =
            byProviders.entries.fold(PriceResults.empty()) { results, (provider, assets) ->
                log.debug("marketDataProvider: ${provider.getId()}, assets: ${assets.size}")

                val priceDate = getPriceDateForProvider(provider, assets, priceRequest)
                val existingPrices = getExistingPrices(provider, assets, priceDate)

                val (foundInDb, remainingAssets) = processExistingPrices(existingPrices, assets)
                val newPrices = getNewPricesFromProvider(remainingAssets, priceDate, provider, priceRequest)

                results
                    .addFoundInDb(foundInDb)
                    .addNewPrices(newPrices, provider.isApiSupported())
            }

        logPriceResults(priceResults)
        saveApiPricesAsync(priceResults.foundOverApi)

        return PriceResponse(priceResults.getAllPrices())
    }

    /**
     * Immutable data class to hold price results from different sources.
     */
    private data class PriceResults(
        val foundInDb: List<MarketData> = emptyList(),
        val foundOverApi: List<MarketData> = emptyList(),
        val customPrice: List<MarketData> = emptyList()
    ) {
        companion object {
            fun empty() = PriceResults()
        }

        fun addFoundInDb(prices: List<MarketData>): PriceResults = copy(foundInDb = foundInDb + prices)

        fun addNewPrices(
            prices: Collection<MarketData>,
            isApiSupported: Boolean
        ): PriceResults =
            when {
                isApiSupported -> copy(foundOverApi = foundOverApi + prices)
                else -> copy(customPrice = customPrice + prices)
            }

        fun getAllPrices(): List<MarketData> = foundInDb + foundOverApi + customPrice
    }

    /**
     * Immutable data class to hold date cache information.
     */
    private data class DateCache(
        val dates: Map<String, LocalDate>
    ) {
        companion object {
            fun empty() = DateCache(emptyMap())
        }

        fun getDate(timezoneId: String): LocalDate? = dates[timezoneId]

        fun addDate(
            timezoneId: String,
            date: LocalDate
        ): DateCache = copy(dates = dates + (timezoneId to date))
    }

    /**
     * Get the price date for a specific provider and its assets.
     */
    private fun getPriceDateForProvider(
        marketDataProvider: MarketDataPriceProvider,
        assets: Collection<Asset>,
        priceRequest: PriceRequest
    ): LocalDate {
        val dateCache = buildDateCache(marketDataProvider, assets, priceRequest)
        val timezoneId =
            assets
                .first()
                .market.timezone.id
        return dateCache.getDate(timezoneId) ?: getMarketDate(marketDataProvider, assets.first(), priceRequest)
    }

    /**
     * Build immutable date cache for assets.
     */
    private fun buildDateCache(
        marketDataProvider: MarketDataPriceProvider,
        assets: Collection<Asset>,
        priceRequest: PriceRequest
    ): DateCache =
        assets.fold(DateCache.empty()) { cache, asset ->
            val timezoneId = asset.market.timezone.id
            if (cache.getDate(timezoneId) != null) {
                cache
            } else {
                val date = getMarketDate(marketDataProvider, asset, priceRequest)
                cache.addDate(timezoneId, date)
            }
        }

    /**
     * Get existing prices from database or provider.
     */
    private fun getExistingPrices(
        marketDataProvider: MarketDataPriceProvider,
        assets: Collection<Asset>,
        priceDate: LocalDate
    ): List<MarketData> =
        if (marketDataProvider.getId() == CashProviderService.ID) {
            // Cash is constant - get from provider
            getFromProvider(assets.toList(), priceDate, marketDataProvider, PriceRequest()).toList()
        } else {
            // Get from database
            priceService.getMarketData(assets, priceDate)
        }

    /**
     * Process existing prices and return found prices and remaining assets.
     */
    private fun processExistingPrices(
        existingPrices: List<MarketData>,
        assets: Collection<Asset>
    ): Pair<List<MarketData>, List<Asset>> {
        val existingPriceMap = existingPrices.associateBy { it.asset.id }

        val (foundInDb, remainingAssets) =
            assets
                .partition { asset ->
                    existingPriceMap.containsKey(asset.id)
                }.let { (found, remaining) ->
                    val foundPrices =
                        found.mapNotNull { asset ->
                            existingPriceMap[asset.id]?.copy(asset = asset)
                        }
                    Pair(foundPrices, remaining)
                }

        return Pair(foundInDb, remainingAssets)
    }

    /**
     * Get new prices from provider for remaining assets.
     */
    private fun getNewPricesFromProvider(
        remainingAssets: List<Asset>,
        priceDate: LocalDate,
        marketDataProvider: MarketDataPriceProvider,
        priceRequest: PriceRequest
    ): Collection<MarketData> = getFromProvider(remainingAssets, priceDate, marketDataProvider, priceRequest)

    /**
     * Log price results summary.
     */
    private fun logPriceResults(priceResults: PriceResults) {
        if (priceResults.foundInDb.size + priceResults.foundOverApi.size > 1) {
            log.debug(
                "From DB: ${priceResults.foundInDb.size}, " +
                    "from API: ${priceResults.foundOverApi.size}, " +
                    "custom prices: ${priceResults.customPrice.size}"
            )
        }
    }

    /**
     * Save API prices asynchronously.
     */
    private fun saveApiPricesAsync(apiPrices: List<MarketData>) {
        if (apiPrices.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                priceService.handle(PriceResponse(apiPrices))
            }
        }
    }

    private fun getMarketData(
        asset: Asset,
        priceRequest: PriceRequest,
        marketDate: LocalDate
    ): MarketData? =
        priceService
            .getMarketData(
                asset.id,
                marketDate,
                priceRequest.closePrice
            ).orElse(null)

    private fun getFromProvider(
        providerAssets: Collection<Asset>,
        priceDate: LocalDate,
        marketDataPriceProvider: MarketDataPriceProvider,
        request: PriceRequest
    ): Collection<MarketData> {
        if (providerAssets.isEmpty()) {
            return emptyList()
        }

        val assetInputs = providerUtils.getInputs(providerAssets.toList())
        val priceRequest =
            PriceRequest(
                priceDate.toString(),
                assets = assetInputs,
                currentMode = request.currentMode,
                closePrice = request.closePrice
            )
        return marketDataPriceProvider.getMarketData(priceRequest)
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
        val priceAssets = listOf(PriceAsset(asset))
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
        response?.let { priceService.purge(it) }
    }
}