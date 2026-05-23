package com.beancounter.agent.tools

import com.beancounter.agent.client.PositionClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal

/**
 * Confirms [PositionTools] delegates correctly and exposes an aggregated
 * variant that the LLM uses when reviewing holdings across many portfolios.
 */
class PositionToolsTest {
    private val usd = Currency("USD")
    private val nzd = Currency("NZD")
    private val nasdaq = Market("NASDAQ", "NASDAQ", currencyId = "USD", currency = usd)
    private val portfolio =
        Portfolio(
            id = "pf-1",
            code = "AGG",
            name = "Aggregated",
            currency = nzd,
            base = usd,
            marketValue = BigDecimal("500000.00"),
            irr = BigDecimal("0.11")
        )
    private val scrubber = ResponseScrubber()

    private fun samplePositions(): Positions {
        val asset = Asset(code = "AAPL", market = nasdaq, category = "Equity")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal("50")
        val money = position.getMoneyValues(Position.In.PORTFOLIO)
        money.weight = BigDecimal("0.125")
        money.irr = BigDecimal("0.14")
        return Positions(portfolio).apply {
            add(position)
            asAt = "2026-04-16"
        }
    }

    @Test
    fun `getAggregatedPositions delegates to client and scrubs without weight`() {
        val client =
            mock<PositionClient> {
                on { getAggregatedPositions(listOf("A", "B"), "today") } doReturn
                    PositionResponse(samplePositions())
            }
        val tools = PositionTools(client, scrubber)

        val result = tools.getAggregatedPositions("A,B", "today")

        assertThat(result.cols).doesNotContain("weight")
        assertThat(result.rows).hasSize(1)
        assertThat(result.cols).contains("assetCode", "xirr", "category")
    }

    @Test
    fun `getAggregatedPositions tolerates spaces and empty entries in code list`() {
        val client =
            mock<PositionClient> {
                on { getAggregatedPositions(listOf("A", "B"), "today") } doReturn
                    PositionResponse(samplePositions())
            }
        val tools = PositionTools(client, scrubber)

        val result = tools.getAggregatedPositions(" A , , B ", "today")

        assertThat(result.rows).hasSize(1)
    }
}