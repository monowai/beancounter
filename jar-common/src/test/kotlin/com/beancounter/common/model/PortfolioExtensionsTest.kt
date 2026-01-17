package com.beancounter.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for Portfolio extension functions.
 */
class PortfolioExtensionsTest {
    private val usd = Currency("USD")
    private val owner = SystemUser("test-user")

    @Test
    fun `setMarketValue should create portfolio with updated values`() {
        // Given
        val original =
            Portfolio(
                id = "test-portfolio",
                code = "TEST",
                name = "Test Portfolio",
                currency = usd,
                base = usd,
                owner = owner
            )

        // When
        val updated =
            original.setMarketValue(
                marketValue = BigDecimal("100000.00"),
                irr = BigDecimal("0.15")
            )

        // Then
        assertThat(updated.id).isEqualTo("test-portfolio")
        assertThat(updated.code).isEqualTo("TEST")
        assertThat(updated.name).isEqualTo("Test Portfolio")
        assertThat(updated.marketValue).isEqualTo(BigDecimal("100000.00"))
        assertThat(updated.irr).isEqualTo(BigDecimal("0.15"))
        assertThat(updated.gainOnDay).isEqualTo(BigDecimal.ZERO)
        assertThat(updated.assetClassification).isEmpty()
        assertThat(updated.valuedAt).isNull()
    }

    @Test
    fun `portfolio with null gainOnDay and assetClassification should deserialize correctly`() {
        // Given - a portfolio with null optional fields (simulating deserialization from old data)
        val portfolio =
            Portfolio(
                id = "test-portfolio",
                code = "TEST",
                name = "Test Portfolio",
                currency = usd,
                base = usd,
                owner = owner,
                gainOnDay = null,
                valuedAt = null
            )

        // Then - nullable fields should be null
        assertThat(portfolio.gainOnDay).isNull()
        assertThat(portfolio.assetClassification).isNull()
        assertThat(portfolio.valuedAt).isNull()
    }

    @Test
    fun `setMarketValue should include gainOnDay when provided`() {
        // Given
        val original =
            Portfolio(
                id = "test-portfolio",
                code = "TEST",
                name = "Test Portfolio",
                currency = usd,
                base = usd,
                owner = owner
            )

        // When
        val updated =
            original.setMarketValue(
                marketValue = BigDecimal("100000.00"),
                irr = BigDecimal("0.15"),
                gainOnDay = BigDecimal("250.00")
            )

        // Then
        assertThat(updated.gainOnDay).isEqualTo(BigDecimal("250.00"))
    }

    @Test
    fun `setMarketValue should include assetClassification when provided`() {
        // Given
        val original =
            Portfolio(
                id = "test-portfolio",
                code = "TEST",
                name = "Test Portfolio",
                currency = usd,
                base = usd,
                owner = owner
            )
        val classification =
            mapOf(
                "Equity" to BigDecimal("70000.00"),
                "Cash" to BigDecimal("20000.00"),
                "RE" to BigDecimal("10000.00")
            )

        // When
        val updated =
            original.setMarketValue(
                marketValue = BigDecimal("100000.00"),
                irr = BigDecimal("0.15"),
                gainOnDay = BigDecimal("250.00"),
                assetClassification = classification
            )

        // Then
        assertThat(updated.assetClassification).hasSize(3)
        assertThat(updated.assetClassification?.get("Equity")).isEqualTo(BigDecimal("70000.00"))
        assertThat(updated.assetClassification?.get("Cash")).isEqualTo(BigDecimal("20000.00"))
        assertThat(updated.assetClassification?.get("RE")).isEqualTo(BigDecimal("10000.00"))
    }

    @Test
    fun `setMarketValue should preserve original portfolio properties`() {
        // Given
        val gbp = Currency("GBP")
        val original =
            Portfolio(
                id = "multi-currency",
                code = "MULTI",
                name = "Multi Currency",
                currency = gbp,
                base = usd,
                owner = owner
            )

        // When
        val updated =
            original.setMarketValue(
                marketValue = BigDecimal("50000.00"),
                irr = BigDecimal("0.10"),
                gainOnDay = BigDecimal("-100.00"),
                assetClassification = mapOf("Equity" to BigDecimal("50000.00"))
            )

        // Then - original properties preserved
        assertThat(updated.currency.code).isEqualTo("GBP")
        assertThat(updated.base.code).isEqualTo("USD")
        assertThat(updated.owner.id).isEqualTo("test-user")
        // New values applied
        assertThat(updated.marketValue).isEqualTo(BigDecimal("50000.00"))
        assertThat(updated.irr).isEqualTo(BigDecimal("0.10"))
        assertThat(updated.gainOnDay).isEqualTo(BigDecimal("-100.00"))
    }

    @Test
    fun `setMarketValue should include valuedAt when provided`() {
        // Given
        val original =
            Portfolio(
                id = "test-portfolio",
                code = "TEST",
                name = "Test Portfolio",
                currency = usd,
                base = usd,
                owner = owner
            )
        val valuedAt = LocalDate.of(2024, 6, 15)

        // When
        val updated =
            original.setMarketValue(
                marketValue = BigDecimal("100000.00"),
                irr = BigDecimal("0.15"),
                gainOnDay = BigDecimal("500.00"),
                assetClassification = mapOf("Equity" to BigDecimal("100000.00")),
                valuedAt = valuedAt
            )

        // Then
        assertThat(updated.valuedAt).isEqualTo(valuedAt)
    }
}