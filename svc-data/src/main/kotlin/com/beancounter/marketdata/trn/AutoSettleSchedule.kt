package com.beancounter.marketdata.trn

import com.beancounter.common.utils.DateUtils
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Scheduled task to auto-settle PROPOSED transactions when their tradeDate arrives.
 *
 * Runs daily to check for PROPOSED transactions (e.g., dividends) where the
 * tradeDate (pay date) has arrived, and automatically transitions them to SETTLED status.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AutoSettleSchedule(
    private val autoSettleService: AutoSettleService,
    private val dateUtils: DateUtils,
    @Value("\${autosettle.schedule:0 0 6 * * *}") private val schedule: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(AutoSettleSchedule::class.java)
    }

    init {
        log.info(
            "AutoSettleSchedule initialized with cron: {}, zone: {}",
            schedule,
            dateUtils.zoneId.id
        )
    }

    @SentryTransaction(operation = "scheduled", name = "AutoSettleSchedule.autoSettle")
    @Scheduled(
        cron = "\${autosettle.schedule:0 0 6 * * *}",
        zone = "#{@scheduleZone}"
    )
    fun autoSettle() {
        log.info(
            "Auto-settle scheduled task starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )
        val settledCount = autoSettleService.autoSettleDueTransactions()
        log.info("Auto-settle completed, {} transactions settled", settledCount)
    }
}