package com.beancounter.marketdata.payslip

import com.beancounter.common.utils.DateUtils
import io.sentry.spring7.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Daily job that publishes the recurring payslips due today, or overdue.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PayslipSchedule(
    private val payslipDueService: PayslipDueService,
    dateUtils: DateUtils,
    @Value("\${payslip.schedule:0 0 5 * * *}") schedule: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(PayslipSchedule::class.java)
    }

    init {
        log.info("PayslipSchedule initialized with cron: {}, zone: {}", schedule, dateUtils.zoneId.id)
    }

    @SentryTransaction(operation = "scheduled", name = "PayslipSchedule.publishDue")
    @Scheduled(
        cron = "\${payslip.schedule:0 0 5 * * *}",
        zone = "#{@scheduleZone}"
    )
    fun publishDue() {
        val published = payslipDueService.publishDue()
        log.info("Recurring payslips: {} due published", published)
    }
}