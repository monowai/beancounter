package com.beancounter.marketdata.providers.eodhd.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate

/**
 * EODHD bulk last-day EOD price row.
 *
 * Endpoint: GET /api/eod-bulk-last-day/{EXCHANGE}?symbols=...&date=...&fmt=json
 * Returns an array of these objects — one per ticker the bulk endpoint
 * resolved. The `code` field (raw symbol without exchange suffix) is what
 * lets the adapter map rows back to the originating BC asset.
 */
data class EodhdBulkPrice(
    val code: String,
    val date: LocalDate,
    val open: BigDecimal = BigDecimal.ZERO,
    val high: BigDecimal = BigDecimal.ZERO,
    val low: BigDecimal = BigDecimal.ZERO,
    val close: BigDecimal = BigDecimal.ZERO,
    @param:JsonProperty("adjusted_close")
    val adjustedClose: BigDecimal = BigDecimal.ZERO,
    val volume: Long = 0L
)