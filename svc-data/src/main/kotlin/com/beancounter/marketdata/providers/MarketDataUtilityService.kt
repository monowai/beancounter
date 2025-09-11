package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.TestEnvironmentUtils
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
     * Delete all prices. Supports testing ONLY!
     *
     * CRITICAL: This method should NEVER be called in production!
     * It will permanently delete ALL market data history.
     */
    fun purge() {
        // SAFEGUARD: Only allow purge in test environments
        if (!TestEnvironmentUtils.isTestEnvironment()) {
            throw IllegalStateException(
                "CRITICAL ERROR: MarketDataUtilityService.purge() method called in non-test environment! " +
                    "This would delete ALL market data history. " +
                    "Current profile: ${System.getProperty("spring.profiles.active")}"
            )
        }

        log.warn("PURGE OPERATION: MarketDataUtilityService.purge() called - this should only happen in tests!")
        priceService.purge()
    }

    fun refresh(
        asset: Asset,
        priceDate: String
    ) {
        val priceAssets = listOf(PriceAsset(asset))

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

        // Check if data already exists for this date - if so, don't fetch from internet
        val existingDataCount = priceService.getMarketDataCount(asset.id, marketDate)

        if (existingDataCount > 0) {
            // Data already exists for this date - maintain price history, don't overwrite
            return
        }

        // Only fetch fresh data if it doesn't exist (building up history)
        val freshData = getMarketDataFromProvider(asset, priceRequest, marketDate)
        if (freshData != null) {
            priceService.handle(PriceResponse(listOf(freshData)))
        }
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

    private fun getMarketDataFromProvider(
        asset: Asset,
        priceRequest: PriceRequest,
        marketDate: LocalDate
    ): MarketData? {
        val providers = providerUtils.splitProviders(listOf(PriceAsset(asset)))
        val provider = providers.keys.iterator().next()

        val assetInputs = providerUtils.getInputs(listOf(asset))
        val freshPriceRequest =
            PriceRequest(
                marketDate.toString(),
                assets = assetInputs,
                currentMode = priceRequest.currentMode,
                closePrice = priceRequest.closePrice
            )

        val marketDataCollection = provider.getMarketData(freshPriceRequest)
        return marketDataCollection.firstOrNull()
    }
}