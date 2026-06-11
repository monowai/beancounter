package com.beancounter.position.schedule

import com.beancounter.common.utils.DateUtils
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Daily cron safety-net that values all portfolios with current market
 * prices. Default `0 30 18 * * Tue-Sat` (18:30 SGT) fires after bc-data's
 * 07:00-18:00 SGT price-refresh window completes. The event-driven
 * [PortfolioRevaluationTrigger] handles intra-day revaluation; this cron
 * guarantees a single end-of-day pass even if no cache-invalidation event
 * fires (e.g. RabbitMQ outage).
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PortfolioValuationSchedule(
    private val revaluator: PortfolioRevaluator,
    private val dateUtils: DateUtils,
    @Value("\${valuation.schedule:0 30 18 * * Tue-Sat}") private val schedule: String
) {
    private val log = LoggerFactory.getLogger(PortfolioValuationSchedule::class.java)

    init {
        log.info(
            "PortfolioValuationSchedule initialized with cron: {}, zone: {}",
            schedule,
            dateUtils.zoneId.id
        )
    }

    @SentryTransaction(operation = "scheduled", name = "PortfolioValuationSchedule.valuePortfolios")
    @Scheduled(
        cron = "\${valuation.schedule:0 30 18 * * Tue-Sat}",
        zone = "#{@scheduleZone}"
    )
    fun valuePortfolios() {
        log.info(
            "Portfolio valuation scheduled task starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )
        revaluator.revalueAll(reason = "cron")
    }
}