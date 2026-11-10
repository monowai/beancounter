package com.beancounter.marketdata.news.sentiment

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Downloads macro topic and sector news and rolls it up into daily sentiment, four times a day so
 * today's count fills in as the day goes. Runs 15 minutes after the macro refresh, clear of the
 * 03:00 article prune and the 03:30 per-asset sentiment refresh.
 *
 * Gated on `schedule.enabled=true` like every other schedule in svc-data.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MarketSentimentSchedule(
    private val marketSentimentService: MarketSentimentService
) {
    @Scheduled(cron = "0 15 */6 * * *", zone = "#{@scheduleZone}")
    fun refresh() {
        // Exception, not Throwable: an Error or interrupt must still reach the scheduler.
        try {
            marketSentimentService.refresh()
        } catch (e: Exception) {
            log.warn("Scheduled market sentiment refresh failed", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MarketSentimentSchedule::class.java)
    }
}