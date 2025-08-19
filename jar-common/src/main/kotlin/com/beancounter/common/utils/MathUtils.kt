package com.beancounter.common.utils

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParseException

/**
 * Controls the way BC will deal with Division and Multiplication when it comes to Fx Rates.
 * This class delegates specific operations to specialized utility classes.
 */
class MathUtils private constructor() {
    companion object {
        private const val MONEY_SCALE = 2
        private val mathContext = MathContext(10)
        private val numberUtils = NumberUtils()

        // Delegate to specialized utilities
        private val calculationUtils = CalculationUtils(MONEY_SCALE, numberUtils)
        private val parsingUtils = ParsingUtils()
        private val validationUtils = ValidationUtils()

        @JvmStatic
        fun getMathContext(): MathContext = mathContext

        // Calculation operations
        @JvmStatic
        fun divide(
            money: BigDecimal?,
            rate: BigDecimal?
        ): BigDecimal = calculationUtils.divide(money, rate)

        @JvmOverloads
        @JvmStatic
        fun multiply(
            money: BigDecimal?,
            rate: BigDecimal?,
            moneyScale: Int = MONEY_SCALE
        ): BigDecimal? = calculationUtils.multiply(money, rate, moneyScale)

        @JvmOverloads
        @JvmStatic
        fun multiplyAbs(
            money: BigDecimal?,
            rate: BigDecimal?,
            moneyScale: Int = MONEY_SCALE
        ): BigDecimal = calculationUtils.multiplyAbs(money, rate, moneyScale)

        @JvmStatic
        fun add(
            value: BigDecimal,
            amount: BigDecimal?
        ): BigDecimal = calculationUtils.add(value, amount)

        // Parsing operations
        @JvmStatic
        @Throws(ParseException::class)
        fun parse(
            value: String?,
            numberFormat: NumberFormat
        ): BigDecimal = parsingUtils.parse(value, numberFormat)

        @JvmStatic
        fun parse(value: String?) = parsingUtils.parse(value)

        @JvmStatic
        operator fun get(money: String?): BigDecimal? = parsingUtils.parseBigDecimal(money)

        // Validation operations
        @JvmStatic
        fun hasValidRate(rate: BigDecimal?): Boolean = validationUtils.hasValidRate(rate)

        @JvmStatic
        fun nullSafe(value: BigDecimal?): BigDecimal = validationUtils.nullSafe(value)
    }

    init {
        throw UnsupportedOperationException("This is a utility class and cannot be instantiated")
    }
}

/**
 * Handles mathematical calculations.
 */
private class CalculationUtils(
    private val moneyScale: Int,
    private val numberUtils: NumberUtils
) {
    fun divide(
        money: BigDecimal?,
        rate: BigDecimal?
    ): BigDecimal {
        if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
            return BigDecimal.ZERO
        }
        return money?.divide(rate, moneyScale, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
    }

    fun multiply(
        money: BigDecimal?,
        rate: BigDecimal?,
        moneyScale: Int
    ): BigDecimal? {
        if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
            return money
        }
        return money!!.multiply(rate).setScale(moneyScale, RoundingMode.HALF_UP)
    }

    fun multiplyAbs(
        money: BigDecimal?,
        rate: BigDecimal?,
        moneyScale: Int
    ): BigDecimal {
        if (numberUtils.isUnset(rate) || numberUtils.isUnset(money)) {
            return BigDecimal.ZERO
        }
        return money!!.multiply(rate).abs().setScale(moneyScale, RoundingMode.HALF_UP)
    }

    fun add(
        value: BigDecimal,
        amount: BigDecimal?
    ): BigDecimal = value.add(amount).setScale(moneyScale, RoundingMode.HALF_UP)
}

/**
 * Handles parsing operations.
 */
private class ParsingUtils {
    @Throws(ParseException::class)
    fun parse(
        value: String?,
        numberFormat: NumberFormat
    ): BigDecimal {
        if (value.isNullOrEmpty() || value.trim().equals("null", ignoreCase = true)) {
            return BigDecimal.ZERO
        }

        return BigDecimal(
            numberFormat.parse(value.trim().replace("\"", "")).toString()
        )
    }

    fun parse(value: String?) = parse(value, NumberFormat.getInstance())

    fun parseBigDecimal(money: String?): BigDecimal? = money?.let { BigDecimal(it) }
}

/**
 * Handles validation operations.
 */
private class ValidationUtils {
    fun hasValidRate(rate: BigDecimal?): Boolean =
        rate != null &&
            rate.compareTo(BigDecimal.ZERO) != 0 &&
            rate.compareTo(BigDecimal.ONE) != 0

    fun nullSafe(value: BigDecimal?): BigDecimal = value ?: BigDecimal.ZERO
}