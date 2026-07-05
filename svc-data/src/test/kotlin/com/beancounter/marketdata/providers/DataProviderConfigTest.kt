package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for [DataProviderConfig.supportsMarketCode] — the shared null-safe helper
 * that drives [isMarketSupported] across all three price providers.
 *
 * Tests the four cases called out in CLEANUP_SPEC.md TASK C:
 *   null markets  — always not supported
 *   blank markets — always not supported (preserves MarketStack's isBlank() guard)
 *   hit           — code present in allowlist → supported
 *   miss          — code absent from allowlist → not supported
 */
internal class DataProviderConfigTest {
    /** Minimal no-op implementation to exercise the interface default method. */
    private val config: DataProviderConfig =
        object : DataProviderConfig {
            override fun getBatchSize() = 1

            override fun getMarketDate(
                market: Market,
                date: String,
                currentMode: Boolean
            ): LocalDate = LocalDate.now()

            override fun getPriceCode(asset: Asset): String = asset.code
        }

    @Test
    fun `supportsMarketCode returns false when markets is null`() {
        assertThat(config.supportsMarketCode(null, "US")).isFalse()
    }

    @Test
    fun `supportsMarketCode returns false when markets is blank`() {
        assertThat(config.supportsMarketCode("", "US")).isFalse()
        assertThat(config.supportsMarketCode("   ", "US")).isFalse()
    }

    @Test
    fun `supportsMarketCode returns true when code is present in allowlist`() {
        assertThat(config.supportsMarketCode("US,ASX,LON", "US")).isTrue()
        assertThat(config.supportsMarketCode("US,ASX,LON", "ASX")).isTrue()
        assertThat(config.supportsMarketCode("US,ASX,LON", "LON")).isTrue()
    }

    @Test
    fun `supportsMarketCode returns false when code is absent from allowlist`() {
        assertThat(config.supportsMarketCode("US,ASX,LON", "SGX")).isFalse()
        assertThat(config.supportsMarketCode("US,ASX,LON", "NZX")).isFalse()
    }

    @Test
    fun `supportsMarketCode rejects substring match - code must be an exact token`() {
        // "SX" is a suffix of "ASX" — substring-contains would incorrectly return true
        assertThat(config.supportsMarketCode("ASX,NZX", "SX")).isFalse()
        // "AS" is a prefix of "ASX"
        assertThat(config.supportsMarketCode("ASX,NZX", "AS")).isFalse()
        // "NZ" is a prefix of "NZX"
        assertThat(config.supportsMarketCode("ASX,NZX", "NZ")).isFalse()
    }

    @Test
    fun `supportsMarketCode trims whitespace around tokens`() {
        // Comma-separated list with spaces around entries still resolves correctly
        assertThat(config.supportsMarketCode("ASX, NZX", "NZX")).isTrue()
        assertThat(config.supportsMarketCode(" ASX , NZX ", "ASX")).isTrue()
    }
}