package com.beancounter.common.utils

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParseException

/**
 * Controls the way BC will deal with Division and Multiplication when it comes to Fx Rates.
 */
class MathUtils private constructor() {
    companion object {
        private const val moneyScale = 2
        private val mathContext = MathContext(10)
        private val numberUtils = NumberUtils()

        @JvmStatic
        fun getMathContext(): MathContext {
            return mathContext
        }

        @JvmStatic
        fun divide(money: BigDecimal?, rate: BigDecimal?): BigDecimal {
            if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
                return BigDecimal.ZERO
            }
            return money!!.divide(rate, moneyScale, RoundingMode.HALF_UP)
        }

        @JvmOverloads
        @JvmStatic
        fun multiply(money: BigDecimal?, rate: BigDecimal?, moneyScale: Int = this.moneyScale): BigDecimal? {
            if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
                return money
            }
            return money!!.multiply(rate).setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @JvmOverloads
        @JvmStatic
        fun multiplyAbs(money: BigDecimal?, rate: BigDecimal?, moneyScale: Int = this.moneyScale): BigDecimal {
            if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
                return BigDecimal.ZERO
            }
            return money!!.multiply(rate).abs().setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @JvmStatic
        fun add(value: BigDecimal, amount: BigDecimal?): BigDecimal {
            return value.add(amount).setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @Throws(ParseException::class)
        @JvmStatic
        fun parse(value: String?, numberFormat: NumberFormat): BigDecimal? {
            if (value == null || value == "null") {
                return null
            }
            return if (value.isBlank()) {
                BigDecimal.ZERO
            } else {
                BigDecimal(numberFormat.parse(value.trim().replace("\"", "")).toString())
            }
        }

        @JvmStatic
        operator fun get(money: String?): BigDecimal? {
            return money?.let { BigDecimal(it) }
        }

        @JvmStatic
        fun hasValidRate(rate: BigDecimal?): Boolean {
            if (rate == null) {
                return false
            }
            return if (rate.compareTo(BigDecimal.ZERO) == 0) {
                false
            } else {
                rate.compareTo(BigDecimal.ONE) != 0
            }
        }

        @JvmStatic
        fun nullSafe(value: BigDecimal?): BigDecimal {
            if (value == null) {
                return BigDecimal.ZERO
            }
            return value
        }

        fun parse(value: String): BigDecimal? {
            return parse(value, NumberFormat.getNumberInstance())
        }
    }

    init {
        throw UnsupportedOperationException("This is a utility class and cannot be instantiated")
    }
}
