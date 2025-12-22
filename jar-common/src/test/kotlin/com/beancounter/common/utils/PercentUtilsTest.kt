package com.beancounter.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Test suite for PercentUtils to ensure percentage calculations work correctly.
 *
 * This class tests:
 * - Basic percentage calculations with valid values
 * - Null value handling (should return zero)
 * - Zero value handling (should return zero)
 * - Custom scale calculations
 * - Decimal value calculations
 *
 * The PercentUtils.percent() method calculates the ratio of currentValue to oldValue
 * by dividing currentValue by oldValue with the specified scale.
 */
class PercentUtilsTest {
    private val percentUtils = PercentUtils()

    @Test
    fun `should calculate percentage correctly with valid values`() {
        // Test basic percentage calculation (currentValue / oldValue)
        val result =
            percentUtils.percent(
                currentValue = BigDecimal("110.00"),
                oldValue = BigDecimal("100.00")
            )
        assertThat(result).isEqualTo(BigDecimal("1.100000"))

        // Test reverse calculation (currentValue / oldValue)
        val reverseResult =
            percentUtils.percent(
                currentValue = BigDecimal("100.00"),
                oldValue = BigDecimal("110.00")
            )
        assertThat(reverseResult).isEqualTo(BigDecimal("0.909091"))
    }

    @Test
    fun `should return zero when values are null`() {
        // Test with null current value
        val result1 =
            percentUtils.percent(
                currentValue = null,
                oldValue = BigDecimal("100.00")
            )
        assertThat(result1).isEqualTo(BigDecimal.ZERO)

        // Test with null old value
        val result2 =
            percentUtils.percent(
                currentValue = BigDecimal("100.00"),
                oldValue = null
            )
        assertThat(result2).isEqualTo(BigDecimal.ZERO)

        // Test with both null values
        val result3 =
            percentUtils.percent(
                currentValue = null,
                oldValue = null
            )
        assertThat(result3).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `should return zero when values are zero`() {
        // Test with zero current value
        val result1 =
            percentUtils.percent(
                currentValue = BigDecimal.ZERO,
                oldValue = BigDecimal("100.00")
            )
        assertThat(result1).isEqualTo(BigDecimal.ZERO)

        // Test with zero old value
        val result2 =
            percentUtils.percent(
                currentValue = BigDecimal("100.00"),
                oldValue = BigDecimal.ZERO
            )
        assertThat(result2).isEqualTo(BigDecimal.ZERO)

        // Test with both zero values
        val result3 =
            percentUtils.percent(
                currentValue = BigDecimal.ZERO,
                oldValue = BigDecimal.ZERO
            )
        assertThat(result3).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `should calculate percentage with custom scale correctly`() {
        val result =
            percentUtils.percent(
                previous = BigDecimal("100.00"),
                current = BigDecimal("110.00"),
                percentScale = 4
            )
        assertThat(result).isEqualTo(BigDecimal("0.9091"))
    }

    @Test
    fun `should calculate percentage with decimal values correctly`() {
        val result =
            percentUtils.percent(
                currentValue = BigDecimal("105.50"),
                oldValue = BigDecimal("100.00")
            )
        assertThat(result).isEqualTo(BigDecimal("1.055000"))
    }

    @Test
    fun `scaleRate should scale to 4 decimal places`() {
        // Basic scaling
        assertThat(percentUtils.scaleRate(BigDecimal("0.07")))
            .isEqualTo(BigDecimal("0.0700"))

        // Already at correct scale
        assertThat(percentUtils.scaleRate(BigDecimal("0.0700")))
            .isEqualTo(BigDecimal("0.0700"))

        // More precision - rounds down
        assertThat(percentUtils.scaleRate(BigDecimal("0.07004")))
            .isEqualTo(BigDecimal("0.0700"))

        // More precision - rounds up (HALF_UP)
        assertThat(percentUtils.scaleRate(BigDecimal("0.07005")))
            .isEqualTo(BigDecimal("0.0701"))

        // Larger value
        assertThat(percentUtils.scaleRate(BigDecimal("1.23456789")))
            .isEqualTo(BigDecimal("1.2346"))
    }

    @Test
    fun `scalePercent should scale to 2 decimal places`() {
        // Basic scaling
        assertThat(percentUtils.scalePercent(BigDecimal("7.11")))
            .isEqualTo(BigDecimal("7.11"))

        // More precision - rounds down
        assertThat(percentUtils.scalePercent(BigDecimal("7.114")))
            .isEqualTo(BigDecimal("7.11"))

        // More precision - rounds up (HALF_UP)
        assertThat(percentUtils.scalePercent(BigDecimal("7.115")))
            .isEqualTo(BigDecimal("7.12"))

        // Whole number
        assertThat(percentUtils.scalePercent(BigDecimal("7")))
            .isEqualTo(BigDecimal("7.00"))

        // Larger value
        assertThat(percentUtils.scalePercent(BigDecimal("123.456")))
            .isEqualTo(BigDecimal("123.46"))
    }
}