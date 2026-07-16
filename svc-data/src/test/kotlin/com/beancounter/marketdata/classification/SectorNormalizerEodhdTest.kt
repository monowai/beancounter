package com.beancounter.marketdata.classification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * EODHD serves Morningstar-style sector names, which differ from AlphaVantage/GICS. Confirms every
 * EODHD ETF sector maps onto a canonical GICS sector so EODHD and AlphaVantage exposures share one
 * vocabulary in the Sector Weightings UI.
 */
class SectorNormalizerEodhdTest {
    private val normalizer = SectorNormalizer()

    @Test
    fun `all eleven EODHD ETF sector names normalize to canonical GICS sectors`() {
        val expected =
            mapOf(
                "Basic Materials" to "Materials",
                "Consumer Cyclicals" to "Consumer Discretionary",
                "Financial Services" to "Financials",
                "Real Estate" to "Real Estate",
                "Communication Services" to "Communication Services",
                "Energy" to "Energy",
                "Industrials" to "Industrials",
                "Technology" to "Information Technology",
                "Consumer Defensive" to "Consumer Staples",
                "Healthcare" to "Health Care",
                "Utilities" to "Utilities"
            )

        expected.forEach { (eodhd, gics) ->
            assertThat(normalizer.normalize(eodhd)).describedAs(eodhd).isEqualTo(gics)
            assertThat(SectorNormalizer.GICS_SECTORS).contains(gics)
        }
    }
}