package com.beancounter.marketdata.fx.fxrates

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

/**
 * exchangeratesapi.io fx rate response contract.
 */
data class ExRatesResponse(
    val base: String,
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    val date: LocalDate,
    val rates: Map<String, BigDecimal>
)