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
 * absolute monetary values, and exposes the documented column set the LLM
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
    fun `position response is columnar with the documented columns`() {
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
        position.dateValues.opened = LocalDate.parse("2025-09-01")
        position.dateValues.last = LocalDate.parse("2026-04-10")
        position.dateValues.lastDividend = LocalDate.parse("2026-02-15")

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
                "opened",
                "lastTrade",
                "lastDividend"
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
                "2025-09-01",
                "2026-04-10",
                "2026-02-15"
            )
    }

    @Test
    fun `null key dates serialise as null in row`() {
        // New / freshly-imported holdings may have opened set but no
        // dividend yet — null must pass through, not crash.
        val asset = Asset(code = "NEW", market = nasdaq, category = "Equity")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal("10")
        position.dateValues.opened = LocalDate.parse("2026-05-17")
        // last + lastDividend left null

        val positions = Positions(portfolio).apply { add(position) }
        val scrubbed = scrubber.scrub(PositionResponse(positions))

        val openedIdx = scrubbed.cols.indexOf("opened")
        val lastTradeIdx = scrubbed.cols.indexOf("lastTrade")
        val lastDividendIdx = scrubbed.cols.indexOf("lastDividend")
        val row = scrubbed.rows.single()
        assertThat(row[openedIdx]).isEqualTo("2026-05-17")
        assertThat(row[lastTradeIdx]).isNull()
        assertThat(row[lastDividendIdx]).isNull()
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
    fun `scrubAggregated drops weight column from cols and rows`() {
        // Aggregated reviews collapse N portfolios into a single dataset
        // for the LLM. The per-position portfolio weight is meaningless
        // once positions are aggregated across portfolios (each position
        // can have a different denominator), and the LLM was observed
        // wasting tokens narrating per-portfolio breakdowns. Drop weight
        // server-side so the input matches a single-portfolio view.
        val asset = Asset(code = "AAPL", market = nasdaq, category = "Equity")
        val position = Position(asset, portfolio)
        position.quantityValues.purchased = BigDecimal("50")
        val money = position.getMoneyValues(Position.In.PORTFOLIO)
        money.weight = BigDecimal("0.125")
        money.irr = BigDecimal("0.14")
        money.priceData =
            PriceData(
                priceDate = LocalDate.parse("2026-04-15"),
                close = BigDecimal("178.50"),
                changePercent = BigDecimal("0.012")
            )

        val positions = Positions(portfolio).apply { add(position) }
        positions.asAt = "2026-04-16"

        val scrubbed = scrubber.scrubAggregated(PositionResponse(positions))

        assertThat(scrubbed.cols).doesNotContain("weight")
        assertThat(scrubbed.cols)
            .containsExactly(
                "assetCode",
                "assetName",
                "market",
                "priceClose",
                "changePercent",
                "xirr",
                "category",
                "opened",
                "lastTrade",
                "lastDividend"
            )
        val row = scrubbed.rows.single()
        assertThat(row).hasSize(scrubbed.cols.size)
        val codeIdx = scrubbed.cols.indexOf("assetCode")
        assertThat(row[codeIdx]).isEqualTo("AAPL")
    }

    @Test
    fun `zero-quantity positions are filtered out before reaching the LLM`() {
        // Closed (zero-quantity) positions clutter analysis prompts and the
        // LLM has been observed surfacing them in commentary even when the
        // system prompt forbids it. Drop them server-side so the row list
        // contains only open holdings.
        val openAsset = Asset(code = "OPEN", market = nasdaq, category = "Equity")
        val openPosition = Position(openAsset, portfolio)
        openPosition.quantityValues.purchased = BigDecimal("10")

        val closedAsset = Asset(code = "OLD", market = nasdaq, category = "Equity")
        val closedPosition = Position(closedAsset, portfolio)
        // quantityValues defaults sum to zero, marking the position closed.

        val positions =
            Positions(portfolio).apply {
                add(openPosition)
                add(closedPosition)
            }
        val scrubbed = scrubber.scrub(PositionResponse(positions))

        val codeIdx = scrubbed.cols.indexOf("assetCode")
        assertThat(scrubbed.rows).hasSize(1)
        assertThat(scrubbed.rows.single()[codeIdx]).isEqualTo("OPEN")
    }
}