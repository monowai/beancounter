package com.beancounter.marketdata.fx

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import io.sentry.spring7.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
 * Coverage: today PLUS `backfillDays` prior calendar days. With the default
 * `backfillDays = 3` the job touches **4 dates per run** (today and the
 * previous 3 days), which catches up after long weekends, public holidays,
 * and svc-data restarts that may have missed the previous trigger.
 *
 * Idempotent — dates already cached are skipped, re-runs are no-ops.
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
    // Number of prior calendar days to also fetch alongside today. Total
    // dates per run = backfillDays + 1. Default 3 → today + 3 prior = 4 dates.
    @Value("\${beancounter.fx.schedule.backfill-days:3}")
    private val backfillDays: Int
) {
    companion object {
        private val log = LoggerFactory.getLogger(FxRateSchedule::class.java)
    }

    // Optional: absent when messaging is disabled (mirrors FxRateService). When
    // present, a freshly pre-warmed date publishes an FX event so svc-position
    // revalues portfolios against the new rates instead of holding stale FX
    // until the next valuation cron.
    private var cacheInvalidationProducer: CacheInvalidationProducer? = null

    @Autowired(required = false)
    fun setCacheInvalidationProducer(producer: CacheInvalidationProducer?) {
        this.cacheInvalidationProducer = producer
    }

    @SentryTransaction(operation = "scheduled", name = "FxRateSchedule.prefetchRates")
    @Scheduled(
        cron = "#{@fxRateScheduleCron}",
        zone = "#{@scheduleZone}"
    )
    fun prefetchRates() {
        val today = LocalDate.now(dateUtils.zoneId)
        log.info(
            "Scheduled FX pre-fetch starting at {} - {} (today + {} prior days = {} dates)",
            today,
            dateUtils.zoneId.id,
            backfillDays,
            backfillDays + 1
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
                cacheInvalidationProducer?.sendFxEvent(date)
                hits++
                log.info("Pre-warmed FX rates for {}: {} rates", date, rates.size)
            } else {
                log.warn("FX provider returned no rates for {}", date)
            }
        }

        log.info("Scheduled FX pre-fetch done — fetched={} skipped={}", hits, skipped)
    }
}