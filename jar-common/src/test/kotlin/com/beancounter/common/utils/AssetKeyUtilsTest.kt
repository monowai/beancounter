package com.beancounter.common.utils

import com.beancounter.common.TestHelpers
import com.beancounter.common.input.AssetInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Test suite for AssetKeyUtils to ensure asset key generation works correctly.
 *
 * This class tests:
 * - Asset key generation from Asset objects
 * - Asset key generation from AssetInput objects
 * - Asset key generation from code and market strings
 * - Special character handling in keys
 * - Edge cases with empty strings
 *
 * The AssetKeyUtils.toKey() method generates keys in the format "assetCode:marketCode".
 */
class AssetKeyUtilsTest {
    @Test
    fun `should generate correct key from asset object`() {
        val asset =
            TestHelpers.createTestAsset(
                code = "AAPL",
                market = "NASDAQ",
                name = "Apple Inc."
            )

        val key = AssetKeyUtils.toKey(asset)
        assertThat(key).isEqualTo("AAPL:NASDAQ")
    }

    @Test
    fun `should generate correct key from asset input object`() {
        val assetInput =
            AssetInput(
                market = "ASX",
                code = "BHP",
                name = "BHP Group"
            )

        val key = AssetKeyUtils.toKey(assetInput)
        assertThat(key).isEqualTo("BHP:ASX")
    }

    @Test
    fun `should generate correct key from code and market strings`() {
        val key = AssetKeyUtils.toKey("MSFT", "NASDAQ")
        assertThat(key).isEqualTo("MSFT:NASDAQ")
    }

    @Test
    fun `should handle special characters in code and market correctly`() {
        val key = AssetKeyUtils.toKey("BRK-B", "NYSE")
        assertThat(key).isEqualTo("BRK-B:NYSE")
    }

    @Test
    fun `should handle empty strings correctly`() {
        val key = AssetKeyUtils.toKey("", "")
        assertThat(key).isEqualTo(":")
    }
}