package com.beancounter.marketdata.macro

import com.beancounter.marketdata.providers.NewsServiceFacade
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Warms macro data every 6 hours: broad topic news, treasury yields, and a Fed rate-expectations
 * snapshot (needed for [RateExpectationsService]'s trend delta, since the public Kalshi API
 * exposes no history of its own).
 *
 * Depends only on [NewsServiceFacade] — the `NewsProvider` public surface — never an EODHD-specific
 * class, so the news feature can be extracted into its own service later without this schedule
 * needing to change. [MACRO_TOPICS] is macro-domain vocabulary and lives here, passed to the news
 * facade as plain strings.
 *
 * Gated on `schedule.enabled=true`, same pattern as
 * [com.beancounter.marketdata.providers.eodhd.news.NewsRetentionSchedule]. Each of the three
 * steps is wrapped independently so one upstream outage doesn't skip the other two.
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MacroRefreshSchedule(
    private val newsServiceFacade: NewsServiceFacade,
    private val treasuryYieldService: TreasuryYieldService,
    private val rateExpectationsService: RateExpectationsService
) {
    @Scheduled(cron = "0 0 */6 * * *", zone = "#{@scheduleZone}")
    fun refresh() {
        runCatching { newsServiceFacade.getTopicNews(MACRO_TOPICS) }
            .onFailure { log.warn("Macro topic news refresh failed: {}", it.message) }
        runCatching { treasuryYieldService.getYields() }
            .onFailure { log.warn("Treasury yield refresh failed: {}", it.message) }
        runCatching { rateExpectationsService.snapshot() }
            .onFailure { log.warn("Rate expectations snapshot failed: {}", it.message) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MacroRefreshSchedule::class.java)

        val MACRO_TOPICS =
            listOf(
                "stock markets",
                "economy",
                "inflation",
                "bonds",
                "energy",
                "commodities"
            )
    }
}