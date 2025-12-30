package com.beancounter.common.trn

import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for TrnType enum stability.
 *
 * NOTE: As of the Flyway migration V1, TrnType is now stored as STRING in the database
 * using @Enumerated(EnumType.STRING). The ordinal position no longer matters for persistence.
 * New values can be added anywhere in the enum, though adding at the end is still recommended
 * for consistency.
 */
class TrnTypeTest {
    @Test
    fun `enum values are defined correctly`() {
        // Verify all expected enum values exist
        val expectedValues =
            listOf(
                "SELL",
                "BUY",
                "SPLIT",
                "DEPOSIT",
                "WITHDRAWAL",
                "DIVI",
                "FX_BUY",
                "IGNORE",
                "BALANCE",
                "ADD",
                "INCOME",
                "DEDUCTION",
                "REDUCE"
            )
        val actualValues = TrnType.entries.map { it.name }
        assertThat(actualValues).containsExactlyInAnyOrderElementsOf(expectedValues)
    }

    @Test
    fun `enum count must match expected to detect additions`() {
        // Update this count when adding new TrnType values.
        assertThat(TrnType.entries).hasSize(13)
    }

    @Test
    fun `cash impact classifications are correct`() {
        // Credits cash (positive impact)
        assertThat(TrnType.isCashCredited(TrnType.DEPOSIT)).isTrue()
        assertThat(TrnType.isCashCredited(TrnType.SELL)).isTrue()
        assertThat(TrnType.isCashCredited(TrnType.DIVI)).isTrue()
        assertThat(TrnType.isCashCredited(TrnType.INCOME)).isTrue()

        // Debits cash (negative impact)
        assertThat(TrnType.isCashDebited(TrnType.BUY)).isTrue()
        assertThat(TrnType.isCashDebited(TrnType.WITHDRAWAL)).isTrue()
        assertThat(TrnType.isCashDebited(TrnType.FX_BUY)).isTrue()
        assertThat(TrnType.isCashDebited(TrnType.DEDUCTION)).isTrue()

        // No cash impact
        assertThat(TrnType.isCashImpacted(TrnType.SPLIT)).isFalse()
        assertThat(TrnType.isCashImpacted(TrnType.ADD)).isFalse()
        assertThat(TrnType.isCashImpacted(TrnType.REDUCE)).isFalse()
        assertThat(TrnType.isCashImpacted(TrnType.BALANCE)).isFalse()
    }

    @Test
    fun `cash transaction types are identified correctly`() {
        assertThat(TrnType.isCash(TrnType.DEPOSIT)).isTrue()
        assertThat(TrnType.isCash(TrnType.WITHDRAWAL)).isTrue()
        assertThat(TrnType.isCash(TrnType.INCOME)).isTrue()
        assertThat(TrnType.isCash(TrnType.DEDUCTION)).isTrue()

        // Non-cash transactions
        assertThat(TrnType.isCash(TrnType.BUY)).isFalse()
        assertThat(TrnType.isCash(TrnType.SELL)).isFalse()
        assertThat(TrnType.isCash(TrnType.DIVI)).isFalse()
    }
}