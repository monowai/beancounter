package com.beancounter.marketdata.classification

import com.beancounter.common.utils.DateUtils
import io.sentry.spring7.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Scheduled refresh of asset classification data.
 * Runs daily to keep ETF sector exposures and equity classifications up to date; for US filers
 * the equity pass also snapshots SEC fundamentals. Each run only attempts a bounded batch of
 * assets due a recheck (see `ClassificationRefreshService`), so a daily cadence - not weekly - is
 * needed for the full population to cycle through in a reasonable time.
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
     * Refresh ETF sector exposures daily.
     * Runs every day at 6:00 AM in the configured timezone.
     */
    @SentryTransaction(operation = "scheduled", name = "ClassificationSchedule.refreshEtfSectors")
    @Scheduled(cron = "0 0 6 * * *", zone = "#{@scheduleZone}")
    fun refreshEtfSectors() {
        log.info(
            "Scheduled ETF sector refresh starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )
        val result = classificationRefreshService.refreshEtfSectors()
        log.info("Scheduled ETF sector refresh complete: $result")
    }

    /**
     * Refresh equity sector/industry daily, half an hour after the ETF pass.
     */
    @SentryTransaction(operation = "scheduled", name = "ClassificationSchedule.refreshEquityClassifications")
    @Scheduled(cron = "0 30 6 * * *", zone = "#{@scheduleZone}")
    fun refreshEquityClassifications() {
        log.info(
            "Scheduled equity classification refresh starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )
        val result = classificationRefreshService.refreshEquityClassifications()
        log.info("Scheduled equity classification refresh complete: $result")
    }
}