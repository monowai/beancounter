package com.beancounter.common.utils

import com.beancounter.common.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Test suite for CashUtils to ensure cash asset detection works correctly.
 *
 * This class tests:
 * - Cash asset identification (assets with category "CASH")
 * - Non-cash asset identification (stocks, bonds, etc.)
 * - Edge cases with different asset categories
 *
 * The CashUtils.isCash() method checks if an asset's category is "CASH".
 */
class CashUtilsTest {
    private val cashUtils = CashUtils()

    @Test
    fun `should return true when asset is cash`() {
        val cashAsset =
            TestHelpers.createTestAsset(
                code = "USD",
                market = "CASH",
                name = "US Dollar",
                category = "CASH"
            )

        assertThat(cashUtils.isCash(cashAsset)).isTrue()
    }

    @Test
    fun `should return false when asset is not cash`() {
        val stockAsset =
            TestHelpers.createTestAsset(
                code = "AAPL",
                market = "NASDAQ",
                name = "Apple Inc.",
                category = "STOCK"
            )

        assertThat(cashUtils.isCash(stockAsset)).isFalse()
    }

    @Test
    fun `should return false when asset has different cash-like category`() {
        val cashLikeAsset =
            TestHelpers.createTestAsset(
                code = "CASH",
                market = "CASH",
                name = "Cash Equivalent",
                category = "MONEY_MARKET"
            )

        assertThat(cashUtils.isCash(cashLikeAsset)).isFalse()
    }
}