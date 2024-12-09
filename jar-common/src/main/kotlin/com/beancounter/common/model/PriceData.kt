package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
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
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DateUtils.FORMAT
    )
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var priceDate: LocalDate = LocalDate.now(),
    var open: BigDecimal = BigDecimal.ZERO,
    var close: BigDecimal = BigDecimal.ZERO,
    var low: BigDecimal = BigDecimal.ZERO,
    var high: BigDecimal = BigDecimal.ZERO,
    var previousClose: BigDecimal = BigDecimal.ZERO,
    var change: BigDecimal = BigDecimal.ZERO,
    var changePercent: BigDecimal = BigDecimal.ZERO,
    var volume: Int = 0
) {
    companion object {
        fun of(marketData: MarketData): PriceData =
            of(
                marketData,
                BigDecimal.ONE
            )

        fun of(
            mktData: MarketData,
            rate: BigDecimal
        ): PriceData {
            val result =
                PriceData(
                    mktData.priceDate,
                    multiplyAbs(
                        mktData.open,
                        rate
                    ),
                    multiplyAbs(
                        mktData.close,
                        rate
                    ),
                    multiplyAbs(
                        mktData.low,
                        rate
                    ),
                    multiplyAbs(
                        mktData.high,
                        rate
                    ),
                    multiplyAbs(
                        mktData.previousClose,
                        rate
                    ),
                    multiplyAbs(
                        mktData.change,
                        rate
                    ),
                    mktData.changePercent,
                    mktData.volume
                )

            if (MathUtils.hasValidRate(rate) &&
                numberUtils.isSet(result.previousClose) &&
                numberUtils.isSet(result.close)
            ) {
                // Convert
                val change =
                    BigDecimal("1.00")
                        .subtract(
                            percentUtils.percent(
                                result.previousClose,
                                result.close,
                                4
                            )
                        )
                result.changePercent = change
                result.change = result.close.subtract(result.previousClose)
            }
            return result
        }

        private val percentUtils = PercentUtils()
        private val numberUtils = NumberUtils()
    }
}