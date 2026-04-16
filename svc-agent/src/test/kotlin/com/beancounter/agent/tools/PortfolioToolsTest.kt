package com.beancounter.agent.tools

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal

/**
 * Sanity check that [PortfolioTools] delegates to [PortfolioServiceClient]
 * and scrubs the response — no dollar values reach the LLM.
 */
class PortfolioToolsTest {
    private val nzd = Currency("NZD")
    private val usd = Currency("USD")
    private val sample =
        Portfolio(
            id = "pf-1",
            code = "MY_PFOLIO",
            name = "Sample",
            currency = nzd,
            base = usd,
            marketValue = BigDecimal("123456.78"),
            irr = BigDecimal("0.12")
        )

    private val scrubber = ResponseScrubber()

    @Test
    fun `listPortfolios returns scrubbed portfolios with no dollar balances`() {
        val client =
            mock<PortfolioServiceClient> {
                on { portfolios } doReturn PortfoliosResponse(listOf(sample))
            }
        val tools = PortfolioTools(client, scrubber)

        val result = tools.listPortfolios()

        assertThat(result).hasSize(1)
        val only = result.first()
        assertThat(only.code).isEqualTo("MY_PFOLIO")
        assertThat(only.name).isEqualTo("Sample")
        assertThat(only.currency).isEqualTo("NZD")
        assertThat(only.baseCurrency).isEqualTo("USD")
        assertThat(only.irr).isEqualTo(0.12)
        // The shape has no marketValue/gainOnDay/id fields — compile-time guarantee.
    }

    @Test
    fun `getPortfolio returns a scrubbed portfolio with IRR as a ratio`() {
        val client =
            mock<PortfolioServiceClient> {
                on { getPortfolioByCode("MY_PFOLIO") } doReturn sample
            }
        val tools = PortfolioTools(client, scrubber)

        val result = tools.getPortfolio("MY_PFOLIO")

        assertThat(result.code).isEqualTo("MY_PFOLIO")
        assertThat(result.irr).isEqualTo(0.12)
        assertThat(result.currency).isEqualTo("NZD")
        assertThat(result.baseCurrency).isEqualTo("USD")
    }
}