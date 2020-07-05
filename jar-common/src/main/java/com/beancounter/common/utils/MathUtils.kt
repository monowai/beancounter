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
        private const val percentScale = 6
        private val mathContext = MathContext(10)

        @JvmStatic
        fun getMathContext(): MathContext {
            return mathContext
        }

        @JvmStatic
        fun divide(money: BigDecimal?, rate: BigDecimal?): BigDecimal? {
            if (isUnset(rate) || isUnset(money)) {
                return money
            }
            return money!!.divide(rate, moneyScale, RoundingMode.HALF_UP)
        }

        @JvmOverloads
        @JvmStatic
        fun multiply(money: BigDecimal?, rate: BigDecimal?, moneyScale: Int = this.moneyScale): BigDecimal? {
            if (isUnset(rate) || isUnset(money)) {
                return money
            }
            return money!!.multiply(rate).abs().setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @JvmStatic
        fun percent(currentValue: BigDecimal?, oldValue: BigDecimal?): BigDecimal? {
            return percent(currentValue, oldValue, percentScale)
        }

        @JvmStatic
        fun percent(previous: BigDecimal?, current: BigDecimal?, percentScale: Int): BigDecimal? {
            return if (isUnset(previous) || isUnset(current)) {
                null
            } else previous!!.divide(current, percentScale, RoundingMode.HALF_UP)
        }

        // Null and Zero are treated as "unSet"
        @JvmStatic
        fun isUnset(value: BigDecimal?): Boolean {
            return value == null || BigDecimal.ZERO.compareTo(value) == 0
        }

        @JvmStatic
        fun add(value: BigDecimal, amount: BigDecimal?): BigDecimal {
            return value.add(amount).setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @Throws(ParseException::class)
        @JvmStatic
        fun parse(value: String?, numberFormat: NumberFormat): BigDecimal? {
            if (value == null) {
                return null
            }
            return if (value.isBlank()) {
                BigDecimal.ZERO
            } else BigDecimal(numberFormat.parse(value.replace("\"", "")).toString())
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
            } else rate.compareTo(BigDecimal.ONE) != 0
        }

        @JvmStatic
        fun nullSafe(value: BigDecimal?): BigDecimal {
            if (value == null ){
                return BigDecimal.ZERO
            }
            return value
        }

        fun isSet(value: BigDecimal?): Boolean {
            return !isUnset(value)
        }
    }

    init {
        throw UnsupportedOperationException("This is a utility class and cannot be instantiated")
    }
}