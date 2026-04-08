package com.beancounter.agent.tools

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Sanity check that the [@Tool]-bearing methods on [PortfolioTools] forward to
 * the underlying [PortfolioServiceClient]. The Spring AI tool dispatcher is not
 * exercised here — that's an end-to-end concern. We only verify the Kotlin
 * delegation does what its description claims.
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
            base = usd
        )

    @Test
    fun `listPortfolios delegates to the client`() {
        val expected = PortfoliosResponse(listOf(sample))
        val client = mock<PortfolioServiceClient> { on { portfolios } doReturn expected }
        val tools = PortfolioTools(client)

        val result = tools.listPortfolios()

        assertThat(result).isSameAs(expected)
        verify(client).portfolios
    }

    @Test
    fun `getPortfolio delegates to the client by code`() {
        val client =
            mock<PortfolioServiceClient> {
                on { getPortfolioByCode("MY_PFOLIO") } doReturn sample
            }
        val tools = PortfolioTools(client)

        val result = tools.getPortfolio("MY_PFOLIO")

        assertThat(result).isSameAs(sample)
    }
}