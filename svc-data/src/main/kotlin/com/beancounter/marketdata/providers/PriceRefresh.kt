package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.today
import com.beancounter.marketdata.assets.AssetHydrationService
import com.beancounter.marketdata.assets.AssetService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * Update prices.
 */
@Service
class PriceRefresh internal constructor(
    private val assetService: AssetService,
    private val assetHydrationService: AssetHydrationService,
    private val marketDataService: MarketDataService,
    private val dateUtils: DateUtils,
) {

    @Transactional(readOnly = true)
    @Async("priceExecutor")
    fun updatePrices(): CompletableFuture<Int> {
        log.info("Updating Prices {}", LocalDateTime.now(dateUtils.getZoneId()))
        val assetCount = AtomicInteger()
        val assets = assetService.findAllAssets()
        for (asset in assets) {
            val priceRequest = PriceRequest.of(assetHydrationService.hydrateAsset(asset), today)
            marketDataService.getPriceResponse(priceRequest)
            assetCount.getAndIncrement()
        }
        log.info(
            "Price update completed for {} assets @ {} - {}",
            assetCount.get(),
            LocalDateTime.now(dateUtils.getZoneId()),
            dateUtils.getZoneId().id,
        )
        return CompletableFuture.completedFuture(assetCount.get())
    }

    fun refreshPrice(assetId: String, date: String = "TODAY"): PriceResponse {
        log.info("Updating Prices {}", LocalDateTime.now(dateUtils.getZoneId()))
        val asset = assetService.find(assetId)
        val priceRequest = PriceRequest.of(assetHydrationService.hydrateAsset(asset), today)
        marketDataService.refresh(asset, date)
        val response = marketDataService.getPriceResponse(priceRequest)
        log.info(
            "Refreshed asset price for ${asset.name} ",
        )
        return response
    }

    companion object {
        private val log = LoggerFactory.getLogger(PriceRefresh::class.java)
    }
}
