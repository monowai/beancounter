package com.beancounter.common

import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.hasValidRate
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.common.utils.MathUtils.Companion.parse
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal class TestMathUtils {
    private val zeroDec = "0.00"
    private val ten = "10.00"
    private val oneThousandDec = "1000.00"
    private val tenThousand = "10000.00"

    @Test
    fun is_MultiplySafe() {

        Assertions.assertThat(
            multiply(
                BigDecimal(oneThousandDec),
                BigDecimal.ZERO
            )
        )
            .isEqualTo(oneThousandDec)
        Assertions.assertThat(
            multiply(
                BigDecimal(oneThousandDec),
                BigDecimal("0.00")
            )
        )
            .isEqualTo(oneThousandDec)
        Assertions.assertThat(
            multiply(
                BigDecimal(oneThousandDec),
                null
            )
        )
            .isEqualTo(oneThousandDec)
        Assertions.assertThat(
            multiply(
                BigDecimal(oneThousandDec),
                BigDecimal(ten)
            )
        )
            .isEqualTo(tenThousand)
        Assertions.assertThat(
            multiply(
                null,
                BigDecimal(ten)
            )
        )
            .isNull()
    }

    @Test
    fun is_DivideSafe() {
        Assertions.assertThat(
            divide(
                BigDecimal(oneThousandDec),
                BigDecimal(zeroDec)
            )
        )
            .isEqualTo(oneThousandDec)
        Assertions.assertThat(
            divide(
                BigDecimal(oneThousandDec),
                BigDecimal(zeroDec)
            )
        )
            .isEqualTo(oneThousandDec)
        Assertions.assertThat(
            divide(
                BigDecimal(oneThousandDec),
                null
            )
        )
            .isEqualTo(oneThousandDec)
        Assertions.assertThat(
            divide(
                null,
                BigDecimal(ten)
            )
        )
            .isNull()
        Assertions.assertThat(
            divide(
                BigDecimal(oneThousandDec),
                BigDecimal(ten)
            )
        )
            .isEqualTo("100.00")
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
        Assertions.assertThat(hasValidRate(null)).isFalse
        Assertions.assertThat(hasValidRate(BigDecimal.ONE)).isFalse
        Assertions.assertThat(hasValidRate(BigDecimal("1.000"))).isFalse
        Assertions.assertThat(hasValidRate(BigDecimal.TEN)).isTrue
        Assertions.assertThat(hasValidRate(BigDecimal.ZERO)).isFalse
        Assertions.assertThat(hasValidRate(BigDecimal("0.00"))).isFalse
    }
}
