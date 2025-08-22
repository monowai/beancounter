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
 * Update prices.
 */
@Service
class PriceRefresh(
    private val assetFinder: AssetFinder,
    private val marketDataService: MarketDataService,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(PriceRefresh::class.java)

    @Transactional(readOnly = true)
    fun updatePrices(): Int {
        log.info(
            "Updating All Prices {}",
            dateUtils.getFormattedDate().toString()
        )
        val assetCount = AtomicInteger()
        val assets = assetFinder.findAllAssets()
        assets.use { assetStream ->
            for (asset in assetStream) {
                val priceRequest =
                    PriceRequest.of(
                        assetFinder.hydrateAsset(asset),
                        TODAY
                    )
                marketDataService.getPriceResponse(priceRequest)
                assetCount.getAndIncrement()
            }
            log.info(
                "Price update completed for {} assets @ {} - {}",
                assetCount.get(),
                LocalDateTime.now(dateUtils.zoneId),
                dateUtils.zoneId.id
            )
        }
        return assetCount.get()
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