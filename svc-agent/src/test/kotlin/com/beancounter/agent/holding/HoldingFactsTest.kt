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
            weight = BigDecimal("0.08333"),
            averageCost = BigDecimal("0.123456"),
            currentPrice = BigDecimal("1.5"),
            priceDate = LocalDate.parse("2026-09-30"),
            unrealisedGainRatio = HoldingFacts.ratio(BigDecimal("-10"), BigDecimal("100")),
            opened = LocalDate.parse("2025-03-04")
        )

    @Test
    fun `should describe the holding in ratios and unit prices, never amounts`() {
        assertThat(facts.describe().lines()).containsExactly(
            "- Portfolio: TYLER (Tyler), portfolio currency SGD",
            "- Asset: NATO (Nato Corp) on NASDAQ, trade currency USD",
            "- Weight in portfolio: 8.3% (measured in SGD)",
            "- Average cost: 0.1235 USD per unit",
            "- Current price: 1.50 USD (2026-09-30)",
            "- Unrealised gain: -10.0% on cost",
            "- Opened: 2025-03-04"
        )
    }

    @Test
    fun `should leave out what svc-position did not report`() {
        val sparse =
            facts.copy(
                currentPrice = null,
                priceDate = null,
                unrealisedGainRatio = HoldingFacts.ratio(BigDecimal("-10"), BigDecimal.ZERO),
                opened = null
            )

        assertThat(sparse.describe())
            .doesNotContain("Current price")
            .doesNotContain("Unrealised")
            .doesNotContain("Opened")
    }
}