package com.beancounter.marketdata.providers.marketstack.model

import com.beancounter.marketdata.providers.marketstack.MarketStackConfig.Companion.DATE_TIME_FORMAT
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Contract representing MarketStack market data.
 */
data class MarketStackData(
    val open: BigDecimal = BigDecimal.ZERO,
    val close: BigDecimal = BigDecimal.ZERO,
    val low: BigDecimal = BigDecimal.ZERO,
    val high: BigDecimal = BigDecimal.ZERO,
    val volume: Int = 0,
    val adj_high: BigDecimal? = BigDecimal.ZERO,
    val adj_low: BigDecimal? = BigDecimal.ZERO,
    val adj_close: BigDecimal? = BigDecimal.ZERO,
    val adj_open: BigDecimal? = BigDecimal.ZERO,
    val adj_volume: Int? = 0,
    val split_factor: BigDecimal = BigDecimal.ONE,
    val dividend: BigDecimal = BigDecimal.ZERO,
    val symbol: String,
    val exchange: String,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_TIME_FORMAT,
    ) @JsonSerialize(using = LocalDateTimeSerializer::class) @JsonDeserialize(
        using = LocalDateTimeDeserializer::class,
    )
    val date: LocalDateTime,
)
