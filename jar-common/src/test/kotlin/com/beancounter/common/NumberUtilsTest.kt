package com.beancounter.common

import com.beancounter.common.utils.MathUtils
import com.beancounter.common.utils.NumberUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.text.NumberFormat

/**
 * Verify numberUtils functions.
 */
class NumberUtilsTest {
    private val numberUtils = NumberUtils()

    @Test
    fun `isZeroAndNullSafe should correctly identify unset values and handle nulls`() {
        val zeroValues =
            listOf(
                null,
                BigDecimal("0"),
                BigDecimal("0.00")
            )
        zeroValues.forEach { value ->
            assertThat(numberUtils.isUnset(value)).isTrue()
        }

        // Test MathUtils behavior with null and empty string inputs
        assertThat(
            MathUtils.parse(
                null,
                NumberFormat.getInstance()
            )
        ).isZero()
        assertThat(
            MathUtils.parse(
                "",
                NumberFormat.getInstance()
            )
        ).isEqualTo(BigDecimal.ZERO)

        // Check null-safe handling
        assertThat(MathUtils.nullSafe(null)).isEqualTo(BigDecimal.ZERO)
    }
}