package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.eodhd.model.EodhdSplit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure unit test for the EODHD split-string parser. Pinned because the encoding
 * (`"new/old"` as a string fraction) is the part most likely to drift unnoticed.
 */
internal class EodhdSplitTest {
    @Test
    fun `parses a four-for-one split as factor 4`() {
        val split = EodhdSplit(LocalDate.of(2020, 8, 31), "4.000000/1.000000")
        assertThat(split.factor()).isEqualByComparingTo(BigDecimal("4"))
    }

    @Test
    fun `parses a reverse split as a fraction less than one`() {
        val split = EodhdSplit(LocalDate.of(2024, 6, 10), "1/10")
        assertThat(split.factor()).isEqualByComparingTo(BigDecimal("0.1"))
    }

    @Test
    fun `rejects malformed split strings`() {
        val split = EodhdSplit(LocalDate.of(2024, 1, 1), "not-a-fraction")
        assertThatThrownBy { split.factor() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Malformed EODHD split string")
    }
}