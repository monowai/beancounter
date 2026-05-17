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
    fun backFill(
        assetId: String,
        fromDate: LocalDate = LocalDate.now().minusYears(2)
    ) {
        backFill(getAsset(assetId), fromDate)
    }

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