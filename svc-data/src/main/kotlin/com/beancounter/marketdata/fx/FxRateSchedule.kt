package com.beancounter.marketdata.fx

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Pre-warms the FX rate DB cache so user-driven events (corporate-action
 * processing, trade ingestion) on the next trading session don't pay the
 * cold-cache external provider call (observed ~613ms against
 * api.exchangeratesapi.io for a single date on the bc-trn-event-demo
 * handler — see Sentry trace ec26b02a).
 *
 * The job iterates over today plus the configured backfill window and
 * fetches any date that doesn't already have rates cached. Idempotent —
 * existing dates are skipped, re-runs are no-ops.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class FxRateSchedule(
    private val fxProviderService: FxProviderService,
    private val fxRateRepository: FxRateRepository,
    private val dateUtils: DateUtils,
    @Value("\${beancounter.fx.schedule.backfill-days:3}")
    private val backfillDays: Int
) {
    companion object {
        private val log = LoggerFactory.getLogger(FxRateSchedule::class.java)
    }

    @SentryTransaction(operation = "scheduled", name = "FxRateSchedule.prefetchRates")
    @Scheduled(
        cron = "#{@fxRateScheduleCron}",
        zone = "#{@scheduleZone}"
    )
    fun prefetchRates() {
        val today = LocalDate.now(dateUtils.zoneId)
        log.info(
            "Scheduled FX pre-fetch starting at {} - {} (backfill={} days)",
            today,
            dateUtils.zoneId.id,
            backfillDays
        )

        var hits = 0
        var skipped = 0
        for (offset in 0..backfillDays) {
            val date = today.minusDays(offset.toLong())
            val cached = fxRateRepository.findByDateRange(date).any { it.date == date }
            if (cached) {
                skipped++
                continue
            }
            val rates = fxProviderService.getRates(date.toString(), providerId = null)
            if (rates.isNotEmpty()) {
                fxRateRepository.saveAll(rates)
                hits++
                log.info("Pre-warmed FX rates for {}: {} rates", date, rates.size)
            } else {
                log.warn("FX provider returned no rates for {}", date)
            }
        }

        log.info("Scheduled FX pre-fetch done — fetched={} skipped={}", hits, skipped)
    }
}