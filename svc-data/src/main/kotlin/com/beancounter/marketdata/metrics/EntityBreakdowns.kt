package com.beancounter.marketdata.metrics

/**
 * Projection DTOs for grouped entity counts. Used as JPQL constructor expressions
 * (e.g. `SELECT new com.beancounter.marketdata.metrics.MarketCount(a.marketCode, COUNT(a)) ...`)
 * and as the row type for [io.micrometer.core.instrument.MultiGauge] breakdowns
 * registered by [EntityCountMetrics].
 */
data class MarketCount(
    val market: String,
    val count: Long
)

data class TypeCount(
    val type: String,
    val count: Long
)

data class SourceCount(
    val source: String,
    val count: Long
)