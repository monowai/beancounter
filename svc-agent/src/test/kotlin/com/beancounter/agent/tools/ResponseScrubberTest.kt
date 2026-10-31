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
import org.assertj.core.api.Assertions.within
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

    private fun held(
        code: String,
        marketValue: String,
        gainOnDay: String,
        changePercent: String
    ): Position {
        val position = Position(Asset(code = code, market = nasdaq, category = "Equity"), portfolio)
        position.quantityValues.purchased = BigDecimal("10")
        val money = position.getMoneyValues(Position.In.PORTFOLIO)
        money.marketValue = BigDecimal(marketValue)
        money.gainOnDay = BigDecimal(gainOnDay)
        money.priceData = PriceData(close = BigDecimal("1"), changePercent = BigDecimal(changePercent))
        return position
    }

    private fun scrubbed(vararg held: Position) =
        scrubber.scrub(PositionResponse(Positions(portfolio).apply { held.forEach(::add) }))

    @Test
    fun `day change blends positions by value against yesterday's total`() {
        val result =
            scrubbed(
                held("UP", "6300", "300", "0.05"),
                held("DOWN", "3700", "-300", "-0.075")
            )

        assertThat(result.dayChangePercent).isEqualTo(0.0)

        val gain = scrubbed(held("UP", "10500", "500", "0.05"), held("FLAT", "10000", "0", "0"))
        assertThat(gain.dayChangePercent).isCloseTo(0.025, within(0.000001))
    }

    @Test
    fun `day change ignores a stale gain on a position with no price move`() {
        val result =
            scrubbed(
                held("UP", "10500", "500", "0.05"),
                held("SHUT", "10000", "999", "0")
            )

        assertThat(result.dayChangePercent).isCloseTo(0.025, within(0.000001))
    }

    @Test
    fun `day change is unknown when no position moved`() {
        assertThat(scrubbed(held("SHUT", "10000", "0", "0")).dayChangePercent).isNull()
    }

    @Test
    fun `day change is unknown when there is no value to measure against`() {
        assertThat(scrubbed(held("GONE", "0", "0", "0.01")).dayChangePercent).isNull()
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

    @Test
    fun `closed positions are returned with a closed column only when requested`() {
        // Default payload stays open-only and column-stable. When the user
        // names a sold holding, the tool re-calls with includeClosed=true and
        // the LLM needs the closed flag to tell the two apart.
        val openAsset = Asset(code = "OPEN", market = nasdaq, category = "Equity")
        val openPosition = Position(openAsset, portfolio)
        openPosition.quantityValues.purchased = BigDecimal("10")

        val closedAsset = Asset(code = "OLD", market = nasdaq, category = "Equity")
        val closedPosition = Position(closedAsset, portfolio)

        val positions =
            Positions(portfolio).apply {
                add(openPosition)
                add(closedPosition)
            }
        val response = PositionResponse(positions)

        val openOnly = scrubber.scrub(response)
        assertThat(openOnly.cols).doesNotContain("closed")
        assertThat(openOnly.rows).hasSize(1)

        val withClosed = scrubber.scrub(response, includeClosed = true)
        val codeIdx = withClosed.cols.indexOf("assetCode")
        val closedIdx = withClosed.cols.indexOf("closed")
        assertThat(closedIdx).isEqualTo(withClosed.cols.size - 1)
        assertThat(withClosed.rows).hasSize(2)
        assertThat(withClosed.rows.map { it[codeIdx] }).containsExactlyInAnyOrder("OPEN", "OLD")
        assertThat(withClosed.rows.single { it[codeIdx] == "OLD" }[closedIdx]).isEqualTo(true)
        assertThat(withClosed.rows.single { it[codeIdx] == "OPEN" }[closedIdx]).isEqualTo(false)
        withClosed.rows.forEach { assertThat(it).hasSize(withClosed.cols.size) }
    }
}