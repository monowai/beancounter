package com.beancounter.marketdata.macro

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The latest published value of one economic indicator, with the value it replaced.
 *
 * [period] is the reference month the figure describes (first of the month), not the day it was
 * released — a September CPI print is published in October. [prior] is the previous month's figure
 * on the same basis (e.g. last month's payroll change), or null when the history doesn't reach it.
 */
data class EconomicPrint(
    val indicator: String,
    val label: String,
    val unit: String,
    val period: LocalDate,
    val value: BigDecimal,
    val prior: BigDecimal?
)

/**
 * A scheduled release that hasn't happened yet.
 *
 * [releaseDate] is the US Eastern calendar date of the release. [expected] is the market-implied
 * median outcome in [unit] — null when the market doesn't bracket a median, and always null for a
 * Fed decision (its odds are served by `/macro/rate-expectations`). [period] is the reference month
 * the release will describe, null for a Fed decision.
 */
data class UpcomingRelease(
    val indicator: String,
    val label: String,
    val unit: String?,
    val releaseDate: LocalDate,
    val period: LocalDate?,
    val expected: BigDecimal?
)

/**
 * `GET /macro/calendar` response: recent prints and upcoming releases for [country]'s headline
 * economic data. Entries that fail upstream are omitted, not null. [upcoming] is soonest first.
 */
data class EconomicCalendarResponse(
    val asOf: LocalDate,
    val country: String,
    val recent: List<EconomicPrint>,
    val upcoming: List<UpcomingRelease>
)