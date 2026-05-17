package com.beancounter.marketdata.providers.eodhd.news

import com.beancounter.marketdata.providers.eodhd.EodhdNewsProperties
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Daily prune of news articles whose `published` timestamp is older than
 * [EodhdNewsProperties.retentionDays]. CASCADE on the join tables cleans the ticker / tag rows.
 *
 * Gated on `schedule.enabled=true` — same pattern as [com.beancounter.marketdata.providers.PriceSchedule]
 * so the prune doesn't fire in tests or local dev unless explicitly turned on.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NewsRetentionSchedule(
    private val newsArticleRepo: NewsArticleRepo,
    private val newsProperties: EodhdNewsProperties
) {
    @Scheduled(cron = "0 0 3 * * *", zone = "#{@scheduleZone}")
    @Transactional
    fun prune() {
        val cutoff = LocalDateTime.now().minusDays(newsProperties.retentionDays)
        val deleted = newsArticleRepo.deleteByPublishedBefore(cutoff)
        log.info("Pruned {} news articles older than {}", deleted, cutoff)
    }

    companion object {
        private val log = LoggerFactory.getLogger(NewsRetentionSchedule::class.java)
    }
}