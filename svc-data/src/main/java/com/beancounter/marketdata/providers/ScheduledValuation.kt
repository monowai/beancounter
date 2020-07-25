package com.beancounter.marketdata.providers

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.LocalDateTime

@EnableTransactionManagement
@Service
class ScheduledValuation(private val priceRefresh: PriceRefresh) {

    private val dateUtils = DateUtils()
    @Bean
    fun assetsSchedule ( @Value("\${assets.schedule:0 */30 7-18 ? * Tue-Sat}") schedule: String): String {
        log.info("ASSETS_SCHEDULE: {}", schedule)
        return schedule
    }


    @Scheduled(cron = "#{@assetsSchedule}", zone = "#{@scheduleZone}")
    fun updatePrices() {
        log.info("Scheduled price update starting {}", LocalDateTime.now(dateUtils.getZoneId()))
        priceRefresh.updatePrices()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScheduledValuation::class.java)
    }

}