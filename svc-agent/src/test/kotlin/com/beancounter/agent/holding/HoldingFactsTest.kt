package com.beancounter.agent.holding

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class HoldingFactsTest {
    private val facts =
        HoldingFacts(
            portfolioCode = "TYLER",
            portfolioName = "Tyler",
            portfolioCurrency = "SGD",
            assetCode = "NATO",
            assetName = "Nato Corp",
            market = "NASDAQ",
            tradeCurrency = "USD",
            quantity = BigDecimal("12.500"),
            weight = BigDecimal("0.08333"),
            averageCost = BigDecimal("0.123456"),
            currentPrice = BigDecimal("1.5"),
            priceDate = LocalDate.parse("2026-09-30"),
            costBasis = BigDecimal("100"),
            marketValue = BigDecimal("90.004"),
            unrealisedGain = BigDecimal("-10"),
            realisedGain = BigDecimal.ZERO,
            opened = LocalDate.parse("2025-03-04")
        )

    @Test
    fun `should describe the holding with prices to at most four places and amounts to two`() {
        assertThat(facts.describe().lines()).containsExactly(
            "- Portfolio: TYLER (Tyler), portfolio currency SGD",
            "- Asset: NATO (Nato Corp) on NASDAQ, trade currency USD",
            "- Quantity held: 12.5",
            "- Weight in portfolio: 8.3% (measured in SGD)",
            "- Average cost: 0.1235 USD per unit",
            "- Current price: 1.50 USD (2026-09-30)",
            "- Cost basis: 100.00 SGD",
            "- Market value: 90.00 SGD",
            "- Unrealised gain: -10.00 SGD (-10.0% on cost)",
            "- Realised gain: 0.00 SGD",
            "- Opened: 2025-03-04"
        )
    }

    @Test
    fun `should leave out what svc-position did not report`() {
        val sparse = facts.copy(currentPrice = null, priceDate = null, costBasis = BigDecimal.ZERO, opened = null)

        assertThat(sparse.describe())
            .doesNotContain("Current price")
            .doesNotContain("on cost")
            .doesNotContain("Opened")
            .contains("- Unrealised gain: -10.00 SGD\n")
    }
}