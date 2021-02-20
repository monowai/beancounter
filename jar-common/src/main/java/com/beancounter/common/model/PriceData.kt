package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.common.utils.NumberUtils
import com.beancounter.common.utils.PercentUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Representation of market data information pertaining to the price of an asset.
 */
data class PriceData(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.format)
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class) var priceDate: LocalDate?,
    var open: BigDecimal?,
    var close: BigDecimal?,
    var low: BigDecimal?,
    var high: BigDecimal?,
    var previousClose: BigDecimal?,
    var change: BigDecimal?,
    var changePercent: BigDecimal?,
    var volume: Int?
) {
    companion object {
        fun of(marketData: MarketData): PriceData {
            return of(marketData, BigDecimal.ONE)
        }

        fun of(mktData: MarketData, rate: BigDecimal?): PriceData {
            val result = PriceData(
                mktData.priceDate,
                multiply(mktData.open, rate),
                multiply(mktData.close, rate),
                multiply(mktData.low, rate),
                multiply(mktData.high, rate),
                multiply(mktData.previousClose, rate),
                multiply(mktData.change, rate),
                mktData.changePercent,
                mktData.volume
            )

            if (MathUtils.hasValidRate(rate) &&
                numberUtils.isSet(result.previousClose) &&
                numberUtils.isSet(result.close)
            ) {
                // Convert
                val change = BigDecimal("1.00")
                    .subtract(percentUtils.percent(result.previousClose, result.close, 4))
                result.changePercent = change
                result.change = result.close!!.subtract(result.previousClose)
            }
            return result
        }

        private val percentUtils = PercentUtils()
        private val numberUtils = NumberUtils()
    }
}
