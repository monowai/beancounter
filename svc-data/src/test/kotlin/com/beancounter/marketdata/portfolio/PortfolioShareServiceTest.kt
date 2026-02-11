package com.beancounter.marketdata.portfolio

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for PortfolioShareService utility methods.
 */
class PortfolioShareServiceTest {
    @Test
    fun `maskEmail masks middle characters of local part`() {
        assertThat(PortfolioShareService.maskEmail("mike@gmail.com"))
            .isEqualTo("m**e@gmail.com")
    }

    @Test
    fun `maskEmail handles short local parts`() {
        assertThat(PortfolioShareService.maskEmail("ab@gmail.com"))
            .isEqualTo("a*@gmail.com")
    }

    @Test
    fun `maskEmail handles single character local part`() {
        assertThat(PortfolioShareService.maskEmail("a@gmail.com"))
            .isEqualTo("a@gmail.com")
    }

    @Test
    fun `maskEmail handles long email addresses`() {
        val masked = PortfolioShareService.maskEmail("longusername@example.com")
        assertThat(masked).startsWith("l")
        assertThat(masked).endsWith("e@example.com")
        assertThat(masked).contains("*")
    }
}