package com.beancounter.marketdata.providers.marketstack.model

import com.beancounter.marketdata.providers.marketstack.MarketStackConfig.Companion.DATE_TIME_FORMAT
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
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
    @param:JsonProperty("adj_high")
    val adjHigh: BigDecimal? = BigDecimal.ZERO,
    @param:JsonProperty("adj_low")
    val adjLow: BigDecimal? = BigDecimal.ZERO,
    @param:JsonProperty("adj_close")
    val adjClose: BigDecimal? = BigDecimal.ZERO,
    @param:JsonProperty("adj_open")
    val adjOpen: BigDecimal? = BigDecimal.ZERO,
    @param:JsonProperty("adj_volume")
    val adjVolume: Int? = 0,
    @param:JsonProperty("split_factor")
    val splitFactor: BigDecimal = BigDecimal.ONE,
    val dividend: BigDecimal = BigDecimal.ZERO,
    val symbol: String,
    val exchange: String,
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DATE_TIME_FORMAT
    ) @param:JsonSerialize(using = LocalDateTimeSerializer::class) @param:JsonDeserialize(
        using = LocalDateTimeDeserializer::class
    )
    val date: LocalDateTime
)