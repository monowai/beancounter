package com.beancounter.common

import com.beancounter.common.utils.MathUtils
import com.beancounter.common.utils.NumberUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.text.NumberFormat

/**
 * Verify numberUtils functions.
 */
class TestNumberUtils {
    private val numberUtils = NumberUtils()

    @Test
    @Throws(Exception::class)
    fun is_ZeroAndNullSafe() {
        Assertions.assertThat(numberUtils.isUnset(null)).isTrue
        Assertions.assertThat(numberUtils.isUnset(BigDecimal("0"))).isTrue
        Assertions.assertThat(numberUtils.isUnset(BigDecimal("0.00"))).isTrue
        Assertions.assertThat(MathUtils.parse(null, NumberFormat.getInstance())).isNull()
        Assertions.assertThat(MathUtils.parse("", NumberFormat.getInstance())).isEqualTo(BigDecimal.ZERO)
        Assertions.assertThat(MathUtils.nullSafe(null)).isEqualTo(BigDecimal.ZERO)
    }
}
