package com.beancounter.marketdata.macro

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Oil-proxy price move for one benchmark ETF, labelled by what it proxies (not the provider
 * symbol convention) — [series] is `WTI_PROXY` / `BRENT_PROXY`, [symbol] the underlying ticker.
 */
data class OilSeries(
    val series: String,
    val symbol: String,
    val latest: BigDecimal,
    val lookback: BigDecimal,
    val changePercent: BigDecimal
)

/**
 * `GET /macro/indicators` response. [yields] / [oil] entries that fail upstream are simply
 * omitted from the list — this endpoint never 500s for partial provider coverage.
 */
data class MacroIndicatorsResponse(
    val asOf: LocalDate,
    val lookbackDays: Int,
    val yields: List<YieldSeries>,
    val oil: List<OilSeries>
)