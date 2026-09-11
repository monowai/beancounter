package com.beancounter.marketdata.macro

import com.beancounter.marketdata.news.NewsServiceFacade
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Warms macro data every 6 hours: broad topic news, treasury yields, a Fed rate-expectations
 * snapshot (needed for [RateExpectationsService]'s trend delta, since the public Kalshi API
 * exposes no history of its own), and a retention prune of old [MacroObservation] snapshot rows
 * (one row per outcome every 6 hours, forever, unless pruned).
 *
 * Depends only on [NewsServiceFacade] — the `NewsProvider` public surface — never an EODHD-specific
 * class, so the news feature can be extracted into its own service later without this schedule
 * needing to change. [MACRO_TOPICS] is macro-domain vocabulary and lives here, passed to the news
 * facade as plain strings.
 *
 * Gated on `schedule.enabled=true`, same pattern as
 * [com.beancounter.marketdata.news.NewsRetentionSchedule]. Each of the four
 * steps is wrapped independently so one failure doesn't skip the others.
 *
 * The prune step delegates to [RateExpectationsService.prune] rather than pruning in-class: that
 * method needs `@Transactional` (its bulk `@Modifying` delete requires an active transaction), and
 * a self-invoked `@Transactional` method (calling `this.prune()` from within [refresh]) would
 * bypass the Spring proxy and silently run without a transaction. Calling out to a different bean
 * goes through the proxy correctly.
 */
@Service
@EnableConfigurationProperties(MacroScheduleProperties::class)
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MacroRefreshSchedule(
    private val newsServiceFacade: NewsServiceFacade,
    private val treasuryYieldService: TreasuryYieldService,
    private val rateExpectationsService: RateExpectationsService,
    private val scheduleProperties: MacroScheduleProperties = MacroScheduleProperties()
) {
    @Scheduled(cron = "0 0 */6 * * *", zone = "#{@scheduleZone}")
    fun refresh() {
        runCatching { newsServiceFacade.getTopicNews(MACRO_TOPICS) }
            .onFailure { log.warn("Macro topic news refresh failed", it) }
        runCatching { treasuryYieldService.getYields() }
            .onFailure { log.warn("Treasury yield refresh failed", it) }
        runCatching { rateExpectationsService.snapshot() }
            .onFailure { log.warn("Rate expectations snapshot failed", it) }
        runCatching { rateExpectationsService.prune(scheduleProperties.retentionDays) }
            .onFailure { log.warn("Macro observation prune failed", it) }
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