package com.beancounter.marketdata.classification

import com.beancounter.common.utils.DateUtils
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Scheduled refresh of asset classification data.
 * Runs weekly to keep ETF sector exposures up to date.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ClassificationSchedule(
    private val classificationRefreshService: ClassificationRefreshService,
    private val dateUtils: DateUtils
) {
    companion object {
        private val log = LoggerFactory.getLogger(ClassificationSchedule::class.java)
    }

    /**
     * Refresh ETF sector exposures weekly.
     * Runs every Sunday at 6:00 AM in the configured timezone.
     */
    @SentryTransaction(operation = "scheduled", name = "ClassificationSchedule.refreshEtfSectors")
    @Scheduled(cron = "0 0 6 * * SUN", zone = "#{@scheduleZone}")
    fun refreshEtfSectors() {
        log.info(
            "Scheduled ETF sector refresh starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )
        val result = classificationRefreshService.refreshEtfSectors()
        log.info("Scheduled ETF sector refresh complete: $result")
    }
}