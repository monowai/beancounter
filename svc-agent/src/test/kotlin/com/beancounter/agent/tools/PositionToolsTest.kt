package com.beancounter.agent.tools

import com.beancounter.agent.client.PositionClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.LocalDate

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
    private val dateUtils = DateUtils("Asia/Singapore")

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

    // svc-position resolves a literal date to the close BEFORE it, while `today` resolves to the
    // most recent close. An LLM handed `[Current date: 2026-08-14]` naturally passes that date
    // through, and silently gets the prior session's price moves. Normalise it back to `today`.
    @Test
    fun `today's literal date is normalised to today`() {
        val today = LocalDate.now(dateUtils.zoneId).toString()
        val client =
            mock<PositionClient> {
                on { getPositionsByCode("TEST", "today", true) } doReturn PositionResponse(samplePositions())
            }
        val tools = PositionTools(client, scrubber, dateUtils)

        val result = tools.getPositions("TEST", today)

        assertThat(result.rows).hasSize(1)
        verify(client).getPositionsByCode("TEST", "today", true)
    }

    @Test
    fun `an historic date is passed through unchanged`() {
        val historic = LocalDate.now(dateUtils.zoneId).minusDays(5).toString()
        val client =
            mock<PositionClient> {
                on { getPositionsById("pf-1", historic, true) } doReturn PositionResponse(samplePositions())
            }
        val tools = PositionTools(client, scrubber, dateUtils)

        tools.getPositionsByPortfolioId("pf-1", historic)

        verify(client).getPositionsById("pf-1", historic, true)
    }

    @Test
    fun `aggregated positions normalise today's literal date`() {
        val today = LocalDate.now(dateUtils.zoneId).toString()
        val client =
            mock<PositionClient> {
                on { getAggregatedPositions(listOf("A", "B"), "today") } doReturn
                    PositionResponse(samplePositions())
            }
        val tools = PositionTools(client, scrubber, dateUtils)

        val result = tools.getAggregatedPositions("A,B", today)

        assertThat(result.rows).hasSize(1)
        verify(client).getAggregatedPositions(listOf("A", "B"), "today", true)
    }
}