package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.assets.AssetFinder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Update prices with smart retry logic.
 *
 * Only fetches prices for assets that don't have today's price yet,
 * making subsequent schedule runs efficient (only retrying failures).
 */
@Service
class PriceRefresh(
    private val assetFinder: AssetFinder,
    private val marketDataService: MarketDataService,
    private val priceService: PriceService,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(PriceRefresh::class.java)

    /**
     * Update prices for all assets that don't have today's price.
     *
     * This method is idempotent - running multiple times will only fetch
     * prices for assets that failed in previous runs.
     */
    @Transactional(readOnly = true)
    fun updatePrices(): Int {
        val priceDate = dateUtils.getFormattedDate()
        log.info(
            "Scheduled price update starting for {} @ {} - {}",
            priceDate,
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )

        val totalAssets = AtomicInteger()
        val skipped = AtomicInteger()
        val fetched = AtomicInteger()
        val failed = AtomicInteger()

        val assets = assetFinder.findAllAssets()
        assets.use { assetStream ->
            for (asset in assetStream) {
                totalAssets.getAndIncrement()
                val hydratedAsset = assetFinder.hydrateAsset(asset)

                // Skip if we already have today's price
                if (hasTodaysPrice(hydratedAsset)) {
                    skipped.getAndIncrement()
                    continue
                }

                // Fetch price for this asset
                try {
                    val priceRequest = PriceRequest.of(hydratedAsset, TODAY)
                    val response = marketDataService.getPriceResponse(priceRequest)

                    if (response.data.isNotEmpty() && response.data.first().close.signum() > 0) {
                        fetched.getAndIncrement()
                    } else {
                        failed.getAndIncrement()
                        log.debug("No valid price returned for {}", hydratedAsset.code)
                    }
                } catch (e: Exception) {
                    failed.getAndIncrement()
                    log.warn("Failed to fetch price for {}: {}", hydratedAsset.code, e.message)
                }
            }
        }

        log.info(
            "Price update completed @ {} - total: {}, skipped (already priced): {}, fetched: {}, failed: {}",
            LocalDateTime.now(dateUtils.zoneId),
            totalAssets.get(),
            skipped.get(),
            fetched.get(),
            failed.get()
        )

        return fetched.get()
    }

    /**
     * Check if asset already has a valid price for today.
     */
    private fun hasTodaysPrice(asset: Asset): Boolean {
        val priceDate = dateUtils.getFormattedDate()
        return priceService.getMarketDataCount(asset.id, priceDate) > 0
    }

    fun refreshPrice(
        assetId: String,
        date: String = dateUtils.getFormattedDate().toString()
    ): PriceResponse {
        log.info(
            "Updating Prices {} for {}",
            LocalDateTime.now(dateUtils.zoneId),
            assetId
        )
        val asset = getAsset(assetId)
        val priceRequest =
            PriceRequest.of(
                asset,
                date
            )
        marketDataService.refresh(
            asset,
            date
        )
        val response = marketDataService.getPriceResponse(priceRequest)
        log.info(
            "Refreshed asset price for ${asset.name} "
        )
        return response
    }

    private fun getAsset(assetId: String): Asset {
        val asset = assetFinder.find(assetId)
        return asset
    }
}