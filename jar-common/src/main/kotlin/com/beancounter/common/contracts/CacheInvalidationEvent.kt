package com.beancounter.common.contracts

import java.time.LocalDate

enum class CacheChangeType {
    TRANSACTION,
    PRICE,
    FX,

    // Emitted after a deep price-history backfill completes. Consumers
    // should invalidate any cached state derived from prices on or after
    // `fromDate`, regardless of portfolio (the affected portfolios are
    // whichever held the asset during the backfilled range — broader
    // invalidation is acceptable here since the event is rare and we
    // want guaranteed correctness on the next request).
    PRICE_HISTORY
}

data class CacheInvalidationEvent(
    val changeType: CacheChangeType,
    val portfolioId: String? = null,
    val fromDate: LocalDate,
    val assetId: String? = null
)