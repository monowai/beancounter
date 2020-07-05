package com.beancounter.marketdata.providers

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.EnableTransactionManagement

@EnableTransactionManagement
@Service
class ScheduledValuation(private val priceRefresh: PriceRefresh) {
    @Scheduled(cron = "\${beancounter.assets.schedule:0 */30 7-18 ? * Tue-Sat}")
    fun updatePrices() {
        priceRefresh.updatePrices()
        log.info("Scheduled price update dispatched.")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScheduledValuation::class.java)
    }

}