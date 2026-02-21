package com.beancounter.common.contracts

import java.time.LocalDate

enum class CacheChangeType {
    TRANSACTION,
    PRICE,
    FX
}

data class CacheInvalidationEvent(
    val changeType: CacheChangeType,
    val portfolioId: String? = null,
    val fromDate: LocalDate,
    val assetId: String? = null
)