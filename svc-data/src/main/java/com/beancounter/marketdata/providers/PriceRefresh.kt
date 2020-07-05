package com.beancounter.marketdata.providers

import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.service.MarketDataService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@Service
class PriceRefresh internal constructor(
        private val assetService: AssetService,
        private val marketDataService: MarketDataService
) {
    @Transactional(readOnly = true)
    fun updatePrices() {
        log.info("Updating Prices " + Date())
        val assetCount = AtomicInteger()
        val assets = assetService.findAllAssets()
        for (asset in assets!!) {
            val hydrated = assetService.hydrateAsset(asset)
            if (hydrated != null) {
                marketDataService.getFuturePriceResponse(hydrated)
            }

            assetCount.getAndIncrement()

        }
        log.info("Updated {} Prices on {}", assetCount.get(), Date())
    }

    companion object {
        private val log = LoggerFactory.getLogger(PriceRefresh::class.java)
    }

}