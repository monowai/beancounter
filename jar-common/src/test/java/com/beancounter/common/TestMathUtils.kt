package com.beancounter.common

import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.hasValidRate
import com.beancounter.common.utils.MathUtils.Companion.isUnset
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.common.utils.MathUtils.Companion.nullSafe
import com.beancounter.common.utils.MathUtils.Companion.parse
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*

internal class TestMathUtils {
    @Test
    fun is_MultiplySafe() {
        Assertions.assertThat(multiply(
                BigDecimal("1000.00"),
                BigDecimal("0")))
                .isEqualTo("1000.00")
        Assertions.assertThat(multiply(
                BigDecimal("1000.00"),
                BigDecimal("0.00")))
                .isEqualTo("1000.00")
        Assertions.assertThat(multiply(
                BigDecimal("1000.00"),
                null))
                .isEqualTo("1000.00")
        Assertions.assertThat(multiply(
                BigDecimal("1000.00"),
                BigDecimal("10.00")))
                .isEqualTo("10000.00")
        Assertions.assertThat(multiply(
                null,
                BigDecimal("10.00")))
                .isNull()
    }

    @Test
    fun is_DivideSafe() {
        Assertions.assertThat(divide(
                BigDecimal("1000.00"),
                BigDecimal("0")))
                .isEqualTo("1000.00")
        Assertions.assertThat(divide(
                BigDecimal("1000.00"),
                BigDecimal("0.00")))
                .isEqualTo("1000.00")
        Assertions.assertThat(divide(
                BigDecimal("1000.00"),
                null))
                .isEqualTo("1000.00")
        Assertions.assertThat(divide(
                null,
                BigDecimal("10.00")))
                .isNull()
        Assertions.assertThat(divide(BigDecimal("1000.00"),
                BigDecimal("10.00")))
                .isEqualTo("100.00")
    }

    @Test
    @Throws(Exception::class)
    fun is_ZeroAndNullSafe() {
        Assertions.assertThat(isUnset(null)).isTrue()
        Assertions.assertThat(isUnset(BigDecimal("0"))).isTrue()
        Assertions.assertThat(isUnset(BigDecimal("0.00"))).isTrue()
        Assertions.assertThat(parse(null, NumberFormat.getInstance())).isNull()
        Assertions.assertThat(parse("", NumberFormat.getInstance())).isEqualTo(BigDecimal.ZERO)
        Assertions.assertThat(nullSafe(null)).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun is_AdditionWorkingToScale() {
        var scaleMe = BigDecimal("100.992")
        // HalfUp
        Assertions.assertThat(add(scaleMe, scaleMe)).isEqualTo(BigDecimal("201.98"))
        scaleMe = BigDecimal("100.994")
        Assertions.assertThat(add(scaleMe, scaleMe)).isEqualTo(BigDecimal("201.99"))
    }

    @Test
    fun is_MathContextDividesToScale() {
        val costBasis = BigDecimal("1000")
        var total = BigDecimal("500.00")
        Assertions.assertThat(costBasis.divide(total, getMathContext()))
                .isEqualTo(BigDecimal("2"))
        total = BigDecimal("555.00")
        Assertions.assertThat(costBasis.divide(total, getMathContext()))
                .isEqualTo(BigDecimal("1.801801802"))
    }

    @Test
    @Throws(Exception::class)
    fun is_NumberFormat() {
        val numberFormat = NumberFormat.getInstance(Locale.US)
        val result = parse("1,000.99", numberFormat)
        Assertions.assertThat(result).isEqualTo("1000.99")
    }

    @Test
    @Throws(Exception::class)
    fun is_CsvExportedQuotationsHandled() {
        val numberFormat = NumberFormat.getInstance(Locale.US)
        val value = "\"1,180.74\""
        Assertions.assertThat(parse(value, numberFormat))
                .isEqualTo("1180.74")
    }

    @Test
    fun is_ValidRate() {
        Assertions.assertThat(hasValidRate(null)).isFalse()
        Assertions.assertThat(hasValidRate(BigDecimal.ONE)).isFalse()
        Assertions.assertThat(hasValidRate(BigDecimal("1.000"))).isFalse()
        Assertions.assertThat(hasValidRate(BigDecimal.TEN)).isTrue()
        Assertions.assertThat(hasValidRate(BigDecimal.ZERO)).isFalse()
        Assertions.assertThat(hasValidRate(BigDecimal("0.00"))).isFalse()
    }
}