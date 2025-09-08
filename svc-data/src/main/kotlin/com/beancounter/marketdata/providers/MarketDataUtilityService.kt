package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for utility operations related to market data.
 */
@Service
class MarketDataUtilityService(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService
) {
    private val log = LoggerFactory.getLogger(MarketDataUtilityService::class.java)

    /**
     * Delete all prices. Supports testing.
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
        
        // First, purge the existing data for this asset and date
        val existingData = getMarketData(asset, priceRequest, marketDate)
        existingData?.let { priceService.purge(it) }
        
        // Then fetch fresh data from the provider
        val freshData = getMarketDataFromProvider(asset, priceRequest, marketDate)
        freshData?.let { priceService.handle(PriceResponse(listOf(it))) }
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

    private fun getMarketData(
        asset: Asset,
        priceRequest: PriceRequest,
        marketDate: LocalDate
    ) = priceService
        .getMarketData(
            asset.id,
            marketDate,
            priceRequest.closePrice
        ).orElse(null)
    
    private fun getMarketDataFromProvider(
        asset: Asset,
        priceRequest: PriceRequest,
        marketDate: LocalDate
    ): MarketData? {
        val providers = providerUtils.splitProviders(listOf(PriceAsset(asset)))
        val provider = providers.keys.iterator().next()
        
        val assetInputs = providerUtils.getInputs(listOf(asset))
        val freshPriceRequest = PriceRequest(
            marketDate.toString(),
            assets = assetInputs,
            currentMode = priceRequest.currentMode,
            closePrice = priceRequest.closePrice
        )
        
        val marketDataCollection = provider.getMarketData(freshPriceRequest)
        return marketDataCollection.firstOrNull()
    }
}