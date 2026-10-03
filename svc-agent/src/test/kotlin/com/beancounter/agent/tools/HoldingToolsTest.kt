package com.beancounter.agent.tools

import com.beancounter.agent.client.PositionClient
import com.beancounter.agent.client.TrnClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.TrnPayload
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.TrnDto
import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [HoldingTools] answers "how did I do on X in portfolio P" — including a
 * holding the user has since sold — from the user's own trade prices, never
 * quantities or amounts.
 */
class HoldingToolsTest {
    private val usd = Currency("USD")
    private val sgd = Currency("SGD")
    private val us = Market("US", "US", currencyId = "USD", currency = usd)
    private val lse = Market("LSE", "LSE", currencyId = "GBP", currency = Currency("GBP"))
    private val portfolio = Portfolio(id = "pf-dbs", code = "DBS", name = "DBS", currency = sgd, base = sgd)
    private val ring = Asset(code = "RING", id = "ring-us", name = "iShares Global Gold Miners", market = us)

    private fun closedRing(): Position =
        Position(ring, portfolio).apply {
            quantityValues.purchased = BigDecimal("100")
            quantityValues.sold = BigDecimal("-100")
            getMoneyValues(Position.In.TRADE, usd).apply {
                priceData.close = BigDecimal("76.69")
                priceData.priceDate = LocalDate.parse("2026-10-02")
                irr = BigDecimal("0.21")
            }
            // Portfolio-currency close must not leak into a trade-currency comparison.
            getMoneyValues(Position.In.PORTFOLIO, sgd).priceData.close = BigDecimal("98.50")
            dateValues.opened = LocalDate.parse("2025-01-10")
            dateValues.last = LocalDate.parse("2026-05-01")
        }

    private fun trade(
        type: TrnType,
        date: String,
        price: String
    ) = TrnDto(
        id = "$type-$date",
        trnType = type,
        tradeDate = LocalDate.parse(date),
        assetId = ring.id,
        quantity = BigDecimal("50"),
        price = BigDecimal(price),
        tradeAmount = BigDecimal("2500"),
        tradeCurrencyCode = "USD",
        portfolioId = portfolio.id
    )

    private fun tools(vararg held: Position): HoldingTools {
        val positionClient =
            mock<PositionClient> {
                on { getPositionsByCode("DBS", "today", true) } doReturn
                    PositionResponse(Positions(portfolio).apply { held.forEach { add(it) } })
            }
        val trnClient =
            mock<TrnClient> {
                on { getTrades("pf-dbs", "ring-us") } doReturn
                    TrnResponse(
                        TrnPayload(
                            trns =
                                listOf(
                                    trade(TrnType.BUY, "2025-01-10", "41.20"),
                                    trade(TrnType.BUY, "2025-06-02", "48.00"),
                                    trade(TrnType.SELL, "2026-05-01", "70.00")
                                )
                        )
                    )
            }
        return HoldingTools(positionClient, trnClient)
    }

    @Test
    fun `should report a sold holding with its exit price against the current close`() {
        val holding = tools(closedRing()).getHolding("DBS", "ring")

        assertThat(holding.status).isEqualTo(HoldingStatus.CLOSED)
        assertThat(holding.assetCode).isEqualTo("RING")
        assertThat(holding.currency).isEqualTo("USD")
        assertThat(holding.currentPrice).isEqualTo(76.69)
        assertThat(holding.lastSellDate).isEqualTo("2026-05-01")
        assertThat(holding.lastSellPrice).isEqualTo(70.00)
        assertThat(holding.changeSinceLastSell).isCloseTo(0.0956, within(0.0001))
        assertThat(holding.returnRatio).isEqualTo(0.21)
        // Held 2025-01-10 → 2026-05-01: over a year, so svc-position reports XIRR.
        assertThat(holding.returnBasis).isEqualTo(ReturnBasis.ANNUALISED)
        assertThat(holding.trades.map { it.type to it.price })
            .containsExactly("BUY" to 41.20, "BUY" to 48.00, "SELL" to 70.00)
    }

    @Test
    fun `should mark a holding of under a year as a simple, not annualised, return`() {
        // svc-position reports simple ROI below 365 days held; calling it "p.a." misleads.
        val recent = closedRing().apply { dateValues.opened = LocalDate.parse("2026-02-01") }

        assertThat(tools(recent).getHolding("DBS", "RING").returnBasis).isEqualTo(ReturnBasis.SIMPLE)
    }

    @Test
    fun `should never expose quantities or amounts`() {
        val json = ObjectMapper().writeValueAsString(tools(closedRing()).getHolding("DBS", "RING"))

        assertThat(json).doesNotContain("quantity").doesNotContain("amount").doesNotContain("2500")
    }

    @Test
    fun `should say so when the portfolio never held the asset`() {
        val holding = tools(closedRing()).getHolding("DBS", "GLD")

        assertThat(holding.status).isEqualTo(HoldingStatus.NOT_HELD)
        assertThat(holding.trades).isEmpty()
    }

    @Test
    fun `should pick the listing on the requested market`() {
        val lseRing = Asset(code = "RING", id = "ring-lse", market = lse)
        val holding = tools(closedRing(), Position(lseRing, portfolio)).getHolding("DBS", "RING", market = "US")

        assertThat(holding.market).isEqualTo("US")
        assertThat(holding.status).isEqualTo(HoldingStatus.CLOSED)
    }

    @Test
    fun `should ask for a market when the code is listed on more than one`() {
        val lseRing = Asset(code = "RING", id = "ring-lse", market = lse)
        val holding = tools(closedRing(), Position(lseRing, portfolio)).getHolding("DBS", "RING")

        assertThat(holding.status).isEqualTo(HoldingStatus.AMBIGUOUS)
        assertThat(holding.markets).containsExactlyInAnyOrder("US", "LSE")
    }
}