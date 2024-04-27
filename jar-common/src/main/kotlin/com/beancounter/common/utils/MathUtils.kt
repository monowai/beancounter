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
        private const val MONEY_SCALE = 2
        private val mathContext = MathContext(10)
        private val numberUtils = NumberUtils()

        @JvmStatic
        fun getMathContext(): MathContext {
            return mathContext
        }

        @JvmStatic
        fun divide(
            money: BigDecimal?,
            rate: BigDecimal?,
        ): BigDecimal {
            if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
                return BigDecimal.ZERO
            }
            return money?.divide(rate, MONEY_SCALE, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
        }

        @JvmOverloads
        @JvmStatic
        fun multiply(
            money: BigDecimal?,
            rate: BigDecimal?,
            moneyScale: Int = this.MONEY_SCALE,
        ): BigDecimal? {
            if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
                return money
            }
            return money!!.multiply(rate).setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @JvmOverloads
        @JvmStatic
        fun multiplyAbs(
            money: BigDecimal?,
            rate: BigDecimal?,
            moneyScale: Int = this.MONEY_SCALE,
        ): BigDecimal {
            if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
                return BigDecimal.ZERO
            }
            return money!!.multiply(rate).abs().setScale(moneyScale, RoundingMode.HALF_UP)
        }

        @JvmStatic
        fun add(
            value: BigDecimal,
            amount: BigDecimal?,
        ): BigDecimal {
            return value.add(amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        }

        @JvmStatic
        @Throws(ParseException::class)
        fun parse(
            value: String?,
            numberFormat: NumberFormat,
        ): BigDecimal {
            if (value.isNullOrEmpty() || value.trim().equals("null", ignoreCase = true)) {
                return BigDecimal.ZERO
            }

            return BigDecimal(numberFormat.parse(value.trim().replace("\"", "")).toString())
        }

        @JvmStatic
        fun parse(value: String?) = parse(value, NumberFormat.getInstance())

        @JvmStatic
        operator fun get(money: String?): BigDecimal? {
            return money?.let { BigDecimal(it) }
        }

        @JvmStatic
        fun hasValidRate(rate: BigDecimal?): Boolean =
            rate != null && rate.compareTo(BigDecimal.ZERO) != 0 && rate.compareTo(BigDecimal.ONE) != 0

        @JvmStatic
        fun nullSafe(value: BigDecimal?): BigDecimal = value ?: BigDecimal.ZERO
    }

    init {
        throw UnsupportedOperationException("This is a utility class and cannot be instantiated")
    }
}
