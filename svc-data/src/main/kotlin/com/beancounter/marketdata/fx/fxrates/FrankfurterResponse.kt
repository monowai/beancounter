package com.beancounter.marketdata.fx.fxrates

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
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
    @param:JsonSerialize(using = LocalDateSerializer::class)
    @param:JsonDeserialize(using = LocalDateDeserializer::class)
    val date: LocalDate,
    val rates: Map<String, BigDecimal>
)