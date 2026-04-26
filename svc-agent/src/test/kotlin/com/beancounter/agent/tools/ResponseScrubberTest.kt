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
 * Verify [ResponseScrubber] emits the compact columnar position shape, strips
 * absolute monetary values, and exposes only the 9-column field set the LLM
 * needs.
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
    fun `position response is columnar with the 9 documented columns`() {
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
        assertThat(scrubbed.portfolioName).isEqualTo("Test Portfolio")
        assertThat(scrubbed.baseCurrency).isEqualTo("USD")
        assertThat(scrubbed.asAt).isEqualTo("2026-04-16")
        assertThat(scrubbed.overallIrr).isEqualTo(0.11)

        assertThat(scrubbed.cols)
            .containsExactly(
                "assetCode",
                "assetName",
                "market",
                "priceClose",
                "changePercent",
                "xirr",
                "weight",
                "category",
                "closed"
            )
        assertThat(scrubbed.rows).hasSize(1)
        assertThat(scrubbed.rows.first())
            .containsExactly(
                "AAPL",
                "Apple Inc.",
                "NASDAQ",
                178.50,
                0.012,
                0.14,
                0.125,
                "Equity",
                false
            )
    }

    @Test
    fun `dropped fields are not present in cols`() {
        // Compact shape excludes roi, yieldPercent, tradeCurrency, priceDate.
        // asAt at response level supersedes per-row priceDate.
        val asset = Asset(code = "X", market = nasdaq, category = "Equity")
        val position = Position(asset, portfolio)
        val positions = Positions(portfolio).apply { add(position) }

        val scrubbed = scrubber.scrub(PositionResponse(positions))

        assertThat(scrubbed.cols)
            .doesNotContain("roi", "yieldPercent", "tradeCurrency", "priceDate")
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

        val closedIdx = scrubbed.cols.indexOf("closed")
        assertThat(scrubbed.rows.single()[closedIdx]).isEqualTo(true)
    }
}