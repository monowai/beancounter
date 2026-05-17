package com.beancounter.marketdata.providers.eodhd.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate

/**
 * EODHD end-of-day price row.
 *
 * Endpoint: GET /api/eod/{SYMBOL.EXCHANGE}?fmt=json
 * Returns an array of these objects (most recent last when sorted ascending).
 */
data class EodhdPrice(
    val date: LocalDate,
    val open: BigDecimal = BigDecimal.ZERO,
    val high: BigDecimal = BigDecimal.ZERO,
    val low: BigDecimal = BigDecimal.ZERO,
    val close: BigDecimal = BigDecimal.ZERO,
    @param:JsonProperty("adjusted_close")
    val adjustedClose: BigDecimal = BigDecimal.ZERO,
    val volume: Long = 0L
)