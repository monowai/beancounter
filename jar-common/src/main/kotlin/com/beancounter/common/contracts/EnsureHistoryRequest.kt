package com.beancounter.common.contracts

import java.time.LocalDate

/**
 * Asks svc-data to ensure each listed asset has price history covering
 * back to `fromDate`. Scheduling is fire-and-forget — backfills run on
 * `PriceBackfillCoordinator`, deduped + cooldown-protected. Caller gets
 * the scheduled count back; the next data fetch returns the widened
 * range.
 */
data class EnsureHistoryRequest(
    val assetIds: List<String> = listOf(),
    val fromDate: LocalDate
)

data class EnsureHistoryResponse(
    val scheduled: Int
)