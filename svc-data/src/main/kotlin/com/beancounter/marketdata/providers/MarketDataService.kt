package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
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
    private val assetService: AssetService,
    private val assetFinder: AssetFinder,
    private val backfillService: MarketDataBackfillService,
    private val utilityService: MarketDataUtilityService,
    private val priceProcessor: MarketDataPriceProcessor,
    private val priceService: PriceService,
    private val dateUtils: DateUtils
) {
    fun backFill(assetId: String) {
        backfillService.backFill(assetId)
    }

    fun getPriceResponse(
        market: String,
        assetCode: String,
        date: String = TODAY
    ): PriceResponse {
        val asset = getAssetLocally(market, assetCode)
        return if (asset != null) {
            getPriceResponse(PriceRequest(date = date, assets = listOf(PriceAsset(asset))))
        } else {
            PriceResponse(emptyList())
        }
    }

    fun getPriceResponse(assetId: String): PriceResponse {
        val asset = getAsset(assetId)
        return getPriceResponse(PriceRequest(assets = listOf(PriceAsset(asset))))
    }

    @Transactional(readOnly = true)
    fun getAssetPrices(priceRequest: PriceRequest): PriceResponse {
        val withResolvedAssets = assetService.resolveAssets(priceRequest)
        return getPriceResponse(withResolvedAssets)
    }

    @Transactional(readOnly = true)
    fun getBulkAssetPrices(bulkPriceRequest: BulkPriceRequest): BulkPriceResponse {
        val resolvedAssets =
            bulkPriceRequest.assets.mapNotNull { priceAsset ->
                assetService.resolveAsset(priceAsset)
            }
        val dates = bulkPriceRequest.dates.map { dateUtils.getFormattedDate(it) }
        return BulkPriceResponse(priceService.getBulkMarketData(resolvedAssets, dates))
    }

    /**
     * Prices for the request.
     *
     * @param priceRequest to process
     * @return results
     */
    fun getPriceResponse(priceRequest: PriceRequest): PriceResponse = priceProcessor.getPriceResponse(priceRequest)

    fun getMarketDate(
        marketDataPriceProvider: MarketDataPriceProvider,
        asset: Asset,
        priceRequest: PriceRequest
    ): LocalDate = utilityService.getMarketDate(marketDataPriceProvider, asset, priceRequest)

    /**
     * Delete all prices.  Supports testing
     */
    fun purge() {
        utilityService.purge()
    }

    fun refresh(
        asset: Asset,
        priceDate: String
    ) {
        utilityService.refresh(asset, priceDate)
    }

    private fun getAsset(assetId: String): Asset {
        val asset = assetFinder.find(assetId)
        return asset
    }

    private fun getAssetLocally(
        market: String,
        assetCode: String
    ): Asset? = assetFinder.findLocally(AssetInput(market, assetCode))
}