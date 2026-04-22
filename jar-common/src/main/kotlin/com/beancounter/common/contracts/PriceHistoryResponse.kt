package com.beancounter.common.contracts

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Response to a price history request: the hydrated asset and
 * a chronological collection of price points.
 */
data class PriceHistoryResponse(
    val asset: Asset,
    val prices: Collection<PricePoint>
)

/**
 * A single price observation. Lean variant of [MarketData] without the
 * repeated asset reference on every row.
 */
data class PricePoint(
    @param:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.FORMAT)
    @param:JsonSerialize(using = LocalDateSerializer::class)
    @param:JsonDeserialize(using = LocalDateDeserializer::class)
    val priceDate: LocalDate,
    val close: BigDecimal,
    val open: BigDecimal = BigDecimal.ZERO,
    val high: BigDecimal = BigDecimal.ZERO,
    val low: BigDecimal = BigDecimal.ZERO,
    val previousClose: BigDecimal = BigDecimal.ZERO,
    val change: BigDecimal = BigDecimal.ZERO,
    val changePercent: BigDecimal = BigDecimal.ZERO,
    val volume: Int = 0
) {
    companion object {
        fun from(md: MarketData): PricePoint =
            PricePoint(
                priceDate = md.priceDate,
                close = md.close,
                open = md.open,
                high = md.high,
                low = md.low,
                previousClose = md.previousClose,
                change = md.change,
                changePercent = md.changePercent,
                volume = md.volume
            )
    }
}