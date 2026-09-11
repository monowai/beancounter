package com.beancounter.marketdata.macro

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tunable knobs for [MacroRefreshSchedule].
 */
@ConfigurationProperties(prefix = "beancounter.market.macro")
data class MacroScheduleProperties(
    /**
     * [MacroObservation] rows older than this many days are pruned by [MacroRefreshSchedule]'s
     * prune step (via [RateExpectationsService.prune]) — otherwise the table grows unbounded
     * (one row per outcome, every 6 hours, forever). 90 days comfortably covers the current
     * 6-day-minimum trend delta with headroom for longer trend windows later.
     */
    val retentionDays: Long = 90
)