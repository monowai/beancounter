package com.beancounter.marketdata.news

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Daily EODHD sentiment refresh, 30 minutes after [NewsRetentionSchedule]'s prune so the two
 * never contend for the news tables. Gated on `schedule.enabled=true` like every other schedule
 * in svc-data, so it is a no-op bean in tests and local dev unless explicitly turned on.
 *
 * Per-batch failures are already absorbed inside [NewsSentimentService.refresh]; the catch here
 * only covers a failure before the first batch (e.g. the asset query) so the scheduler thread
 * logs it as ours rather than as an unhandled task error.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NewsSentimentSchedule(
    private val newsSentimentService: NewsSentimentService
) {
    @Scheduled(cron = "0 30 3 * * *", zone = "#{@scheduleZone}")
    fun refresh() {
        // Exception, not Throwable: an Error or interrupt must still reach the scheduler.
        try {
            log.info("Scheduled sentiment refresh complete: {}", newsSentimentService.refresh())
        } catch (e: Exception) {
            log.warn("Scheduled sentiment refresh failed", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NewsSentimentSchedule::class.java)
    }
}