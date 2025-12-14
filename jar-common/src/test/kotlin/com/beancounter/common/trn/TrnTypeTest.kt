package com.beancounter.common.trn

import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for TrnType enum stability.
 *
 * CRITICAL: These tests protect against accidentally breaking database serialization.
 * If you need to add new TrnType values, add them at the END of the enum and add
 * corresponding assertions here. NEVER insert values in the middle.
 *
 * See CLAUDE.md "Critical: Enum Ordering Rules" for details.
 */
class TrnTypeTest {
    @Test
    fun `enum ordinals must not change for backward compatibility`() {
        // These ordinal values are persisted in the database.
        // Changing them will cause existing transactions to be misinterpreted.
        // If this test fails, you likely inserted a new enum value in the wrong position.
        // New values MUST be added at the END of the enum.
        assertThat(TrnType.SELL.ordinal).isEqualTo(0)
        assertThat(TrnType.BUY.ordinal).isEqualTo(1)
        assertThat(TrnType.SPLIT.ordinal).isEqualTo(2)
        assertThat(TrnType.DEPOSIT.ordinal).isEqualTo(3)
        assertThat(TrnType.WITHDRAWAL.ordinal).isEqualTo(4)
        assertThat(TrnType.DIVI.ordinal).isEqualTo(5)
        assertThat(TrnType.FX_BUY.ordinal).isEqualTo(6)
        assertThat(TrnType.IGNORE.ordinal).isEqualTo(7)
        assertThat(TrnType.BALANCE.ordinal).isEqualTo(8)
        assertThat(TrnType.ADD.ordinal).isEqualTo(9)
        assertThat(TrnType.INCOME.ordinal).isEqualTo(10)
        assertThat(TrnType.DEDUCTION.ordinal).isEqualTo(11)
    }

    @Test
    fun `enum count must match expected to detect additions`() {
        // Update this count when adding new TrnType values.
        // This ensures new values are consciously added with ordinal assertions above.
        assertThat(TrnType.entries).hasSize(12)
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