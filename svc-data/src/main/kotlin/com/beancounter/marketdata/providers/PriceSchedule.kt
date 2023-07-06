package com.beancounter.marketdata.providers

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Scheduled updated of market prices.
 */
@Service
@ConditionalOnProperty(value = ["schedule.enabled"], havingValue = "true", matchIfMissing = false)
class PriceSchedule(private val priceRefresh: PriceRefresh, private val dateUtils: DateUtils) {

    companion object {
        private val log = LoggerFactory.getLogger(PriceSchedule::class.java)
    }

    @Scheduled(cron = "#{@assetsSchedule}", zone = "#{@scheduleZone}")
    fun updatePrices() {
        log.info(
            "Scheduled price update starting {} - {}",
            LocalDateTime.now(dateUtils.getZoneId()),
            dateUtils.defaultZone,
        )
        priceRefresh.updatePrices()
    }
}
