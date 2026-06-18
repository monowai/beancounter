package com.beancounter.marketdata.providers

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.config.MarketClose
import com.beancounter.marketdata.config.PriceScheduleProperties
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Market-close-aligned scheduled price refresh.
 *
 * Registers one cron trigger per configured [MarketClose] (default UK + US),
 * each evaluated in the market's own timezone so it fires just after that
 * market's EOD prices publish — independent of daylight-saving and of the
 * server's zone. All triggers call the same idempotent [updatePrices]; the
 * per-market availability gate ensures each fire only pulls markets that have
 * actually closed.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PriceSchedule(
    private val priceRefresh: PriceRefresh,
    private val dateUtils: DateUtils,
    private val properties: PriceScheduleProperties
) : SchedulingConfigurer {
    companion object {
        private val log = LoggerFactory.getLogger(PriceSchedule::class.java)
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        properties.closes.forEach { close ->
            log.info(
                "Registering price-refresh trigger '{}' cron='{}' zone='{}'",
                close.name,
                close.cron,
                close.zone
            )
            taskRegistrar.addCronTask(
                CronTask(
                    Runnable { runClose(close) },
                    CronTrigger(
                        close.cron,
                        ZoneId.of(close.zone)
                    )
                )
            )
        }
    }

    private fun runClose(close: MarketClose) {
        log.info(
            "Market-close price refresh '{}' firing ({})",
            close.name,
            close.zone
        )
        updatePrices()
    }

    @SentryTransaction(operation = "scheduled", name = "PriceSchedule.updatePrices")
    fun updatePrices() {
        log.info(
            "Scheduled price update starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )
        priceRefresh.updatePrices()
    }
}