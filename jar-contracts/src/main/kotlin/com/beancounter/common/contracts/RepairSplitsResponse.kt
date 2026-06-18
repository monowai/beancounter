package com.beancounter.common.contracts

/**
 * Outcome of `POST /prices/{assetId}/repair-splits`.
 *
 * The repair walks corporate-event splits for the asset and stamps the
 * `split` column on each ex-date `MarketData` row that still carries the
 * default `1`. Once stamped, `SplitAdjuster` can rebase historical OHLC
 * via column-transition detection on every read path without per-call
 * provider lookups.
 */
data class RepairSplitsResponse(
    val stamped: Int = 0,
    val alreadyStamped: Int = 0,
    val missingRows: Int = 0
)