package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetFinder
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for handling market data backfill operations.
 */
@Service
class MarketDataBackfillService(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService,
    private val assetFinder: AssetFinder
) {
    /**
     * Backfills market data for the asset identified by the given asset ID starting from the specified date.
     *
     * @param assetId The identifier of the asset to backfill.
     * @param fromDate The start date (inclusive) for the backfill; defaults to two years before the current date.
     */
    fun backFill(
        assetId: String,
        fromDate: LocalDate = LocalDate.now().minusYears(2)
    ) {
        backFill(getAsset(assetId), fromDate)
    }

    /**
     * Backfills market data for the given asset starting from the specified date and submits the results to the pricing service.
     *
     * @param asset The asset to backfill market data for.
     * @param fromDate The start date (inclusive) for the backfill; defaults to two years before the current date.
     */
    fun backFill(
        asset: Asset,
        fromDate: LocalDate = LocalDate.now().minusYears(2)
    ) {
        val byFactory = providerUtils.splitProviders(providerUtils.getInputs(listOf(asset)))
        for (marketDataProvider in byFactory.keys) {
            priceService.handle(marketDataProvider.backFill(asset, fromDate))
        }
    }

    private fun getAsset(assetId: String): Asset = assetFinder.find(assetId)
}