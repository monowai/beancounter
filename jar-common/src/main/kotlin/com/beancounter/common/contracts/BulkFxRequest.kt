package com.beancounter.common.contracts

import com.beancounter.common.model.IsoCurrencyPair

/**
 * Request for FX rates for multiple currency pairs.
 *
 * Prefer `dates` (the specific valuation dates needed) over `startDate`/`endDate` —
 * the range form eager-loads every cached FxRate between the two endpoints, which
 * blew the bc-data heap when the wealth-performance "ALL" chart sent a 10y range
 * (incident 2026-05-18, DATA-45). Both forms remain supported for back-compat;
 * when `dates` is non-empty the server narrows its DB load to that set + a small
 * lookback for the nearest-prior fallback path.
 */
data class BulkFxRequest(
    val startDate: String,
    val endDate: String,
    val pairs: Set<IsoCurrencyPair> = emptySet(),
    val dates: List<String> = emptyList()
)