package com.beancounter.marketdata.providers

import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.service.MarketDataService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@Service
class PriceRefresh internal constructor(
        private val assetService: AssetService,
        private val marketDataService: MarketDataService,
        private val dateUtils: DateUtils
) {

    @Transactional(readOnly = true)
    @Async("priceExecutor")
    fun updatePrices() {
        log.info("Updating Prices {}", LocalDateTime.now(dateUtils.getZoneId()))
        val assetCount = AtomicInteger()
        val assets = assetService.findAllAssets()
        for (asset in assets!!) {
            marketDataService.getPriceResponse(AssetInput(assetService.hydrateAsset(asset)))
            assetCount.getAndIncrement()
        }
        log.info("Price update completed for {} assets @ {}", assetCount.get(), LocalDateTime.now(dateUtils.getZoneId()))
    }

    companion object {
        private val log = LoggerFactory.getLogger(PriceRefresh::class.java)
    }

}