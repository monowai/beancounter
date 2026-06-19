package com.beancounter.marketdata.fx.fxrates

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Frankfurter API (frankfurter.dev) FX rate response contract.
 */
data class FrankfurterResponse(
    val amount: BigDecimal = BigDecimal.ONE,
    val base: String,
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    val date: LocalDate,
    val rates: Map<String, BigDecimal>
)