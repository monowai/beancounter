package com.beancounter.agent.tools

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.PriceData
import com.beancounter.common.model.Totals
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Verify [ResponseScrubber] strips absolute monetary values and keeps ratios,
 * weights, percentages, and public price data.
 */
class ResponseScrubberTest {
    private val usd = Currency("USD")
    private val nzd = Currency("NZD")
    private val nasdaq = Market("NASDAQ", "NASDAQ", currencyId = "USD", currency = usd)

    private val portfolio =
        Portfolio(
            id = "pf-1",
            code = "TEST",
            name = "Test Portfolio",
            currency = nzd,
            base = usd,
            marketValue = BigDecimal("500000.00"),
            irr = BigDecimal("0.11")
        )

    private val scrubber = ResponseScrubber()

    @Test
    fun `scrubbed portfolio drops marketValue and id`() {
        val scrubbed = scrubber.scrub(portfolio)
        assertThat(scrubbed.code).isEqualTo("TEST")
        assertThat(scrubbed.currency).isEqualTo("NZD")
        assertThat(scrubbed.baseCurrency).isEqualTo("USD")
        assertThat(scrubbed.irr).isEqualTo(0.11)
    }

    @Test
    fun `scrubbed position exposes only ratios, weight, yield, and public prices`() {
        val asset =
            Asset(
                code = "AAPL",
                name = "Apple Inc.",
                market = nasdaq,
                category = "Equity"
            )
        val position = Position(asset, portfolio)
        val money = position.getMoneyValues(Position.In.PORTFOLIO)
        money.marketValue = BigDecimal("10000.00")
        money.costValue = BigDecimal("7500.00")
        money.unrealisedGain = BigDecimal("2500.00")
        money.dividends = BigDecimal("180.00")
        money.totalGain = BigDecimal("2680.00")
        money.weight = BigDecimal("0.125")
        money.irr = BigDecimal("0.14")
        money.roi = BigDecimal("1.357")
        money.priceData =
            PriceData(
                priceDate = LocalDate.parse("2026-04-15"),
                close = BigDecimal("178.50"),
                changePercent = BigDecimal("0.012")
            )
        position.quantityValues.purchased = BigDecimal("50")

        val positions = Positions(portfolio)
        positions.add(position)
        positions.asAt = "2026-04-16"
        val totals =
            Totals(
                currency = usd,
                marketValue = BigDecimal("500000"),
                irr = BigDecimal("0.11")
            )
        positions.setTotal(Position.In.PORTFOLIO, totals)

        val scrubbed = scrubber.scrub(PositionResponse(positions))

        assertThat(scrubbed.portfolioCode).isEqualTo("TEST")
        assertThat(scrubbed.baseCurrency).isEqualTo("USD")
        assertThat(scrubbed.asAt).isEqualTo("2026-04-16")
        assertThat(scrubbed.overallIrr).isEqualTo(0.11)
        assertThat(scrubbed.positions).hasSize(1)

        val p = scrubbed.positions.first()
        assertThat(p.assetCode).isEqualTo("AAPL")
        assertThat(p.assetName).isEqualTo("Apple Inc.")
        assertThat(p.market).isEqualTo("NASDAQ")
        assertThat(p.category).isEqualTo("Equity")
        assertThat(p.weight).isEqualTo(0.125)
        assertThat(p.xirr).isEqualTo(0.14)
        assertThat(p.roi).isEqualTo(1.357)
        assertThat(p.changePercent).isEqualTo(0.012)
        assertThat(p.priceClose).isEqualTo(178.50)
        assertThat(p.priceDate).isEqualTo("2026-04-15")
        assertThat(p.closed).isFalse()
        // yieldPercent = dividends / marketValue = 180 / 10000 = 0.018
        assertThat(p.yieldPercent).isEqualTo(0.018)
        // The scrubbed shape has no marketValue/costValue/gain fields —
        // compile-time guarantee from ScrubbedPosition's declaration.
    }

    @Test
    fun `zero-quantity position is marked closed`() {
        val asset =
            Asset(
                code = "OLD",
                market = nasdaq,
                category = "Equity"
            )
        val position = Position(asset, portfolio)
        val money = position.getMoneyValues(Position.In.PORTFOLIO)
        money.marketValue = BigDecimal.ZERO
        money.weight = BigDecimal.ZERO
        // quantityValues.total = purchased + sold + adjustment, all zero by default

        val positions = Positions(portfolio)
        positions.add(position)
        val scrubbed = scrubber.scrub(PositionResponse(positions))

        assertThat(scrubbed.positions.single().closed).isTrue()
    }

    @Test
    fun `zero market value produces null yield`() {
        val asset =
            Asset(
                code = "CASH",
                market = nasdaq,
                category = "Cash"
            )
        val position = Position(asset, portfolio)
        val money = position.getMoneyValues(Position.In.PORTFOLIO)
        money.marketValue = BigDecimal.ZERO
        money.dividends = BigDecimal.ZERO
        money.weight = BigDecimal.ZERO

        val positions = Positions(portfolio)
        positions.add(position)
        val scrubbed = scrubber.scrub(PositionResponse(positions))

        assertThat(scrubbed.positions.single().yieldPercent).isNull()
    }
}