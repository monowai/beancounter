package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnGroupBy
import com.beancounter.common.model.Broker
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure fold coverage for the split-adjusted trade drill-down summary. The
 * drill-down must MULTIPLY by a SPLIT ratio (not add it) and apply a
 * portfolio-wide split to every broker holding that asset.
 */
class TrnTradeSummaryBuilderTest {
    private val owner = SystemUser(id = "u1", email = "u1@test.com")
    private val nasdaq = Market("NASDAQ")
    private val asset = getTestAsset(nasdaq, "AAPL")
    private val p1 = Portfolio(id = "p1", code = "P1", owner = owner)
    private val p2 = Portfolio(id = "p2", code = "P2", owner = owner)

    private fun trn(
        type: TrnType,
        qty: String,
        date: String,
        portfolio: Portfolio = p1,
        broker: Broker? = null
    ) = Trn(
        trnType = type,
        asset = asset,
        quantity = BigDecimal(qty),
        portfolio = portfolio,
        broker = broker,
        tradeDate = LocalDate.parse(date)
    )

    private fun qtyFor(
        summary: com.beancounter.common.contracts.TrnTradeSummary,
        groupId: String
    ): BigDecimal = summary.groups.first { it.groupId == groupId }.quantity

    private fun subQtyFor(
        summary: com.beancounter.common.contracts.TrnTradeSummary,
        portfolioId: String,
        brokerId: String
    ): BigDecimal =
        summary.groups
            .first { it.groupId == portfolioId }
            .subTotals
            .first { it.groupId == brokerId }
            .quantity

    @Test
    fun `broker group applies split as a multiply not an add`() {
        val dbs = Broker(name = "DBS", owner = owner)
        // BUY 12, 4:1 SPLIT (broker-null corporate action), SELL 40 post-split.
        val trns =
            listOf(
                trn(TrnType.BUY, "12", "2026-01-01", broker = dbs),
                trn(TrnType.SPLIT, "4", "2026-04-21"),
                trn(TrnType.SELL, "40", "2026-06-23", broker = dbs)
            )

        val summary = TrnTradeSummaryBuilder.build(trns, TrnGroupBy.BROKER)

        // 12 * 4 - 40 = 8, NOT the naive 12 + 4 - 40 = -24.
        assertThat(qtyFor(summary, dbs.id)).isEqualByComparingTo("8")
    }

    @Test
    fun `portfolio-wide split scales every broker holding the asset`() {
        val dbs = Broker(name = "DBS", owner = owner)
        val ib = Broker(name = "IB", owner = owner)
        val trns =
            listOf(
                trn(TrnType.BUY, "10", "2026-01-01", broker = dbs),
                trn(TrnType.BUY, "5", "2026-01-01", broker = ib),
                trn(TrnType.SPLIT, "2", "2026-02-01") // broker-null, whole position
            )

        val summary = TrnTradeSummaryBuilder.build(trns, TrnGroupBy.BROKER)

        assertThat(qtyFor(summary, dbs.id)).isEqualByComparingTo("20")
        assertThat(qtyFor(summary, ib.id)).isEqualByComparingTo("10")
    }

    @Test
    fun `broker buying after the split is not scaled`() {
        val dbs = Broker(name = "DBS", owner = owner)
        val ib = Broker(name = "IB", owner = owner)
        val trns =
            listOf(
                trn(TrnType.BUY, "10", "2026-01-01", broker = dbs),
                trn(TrnType.SPLIT, "2", "2026-02-01"),
                trn(TrnType.BUY, "7", "2026-03-01", broker = ib) // post-split shares
            )

        val summary = TrnTradeSummaryBuilder.build(trns, TrnGroupBy.BROKER)

        assertThat(qtyFor(summary, dbs.id)).isEqualByComparingTo("20")
        assertThat(qtyFor(summary, ib.id)).isEqualByComparingTo("7")
    }

    @Test
    fun `portfolio grouping scopes splits to their own portfolio`() {
        val trns =
            listOf(
                trn(TrnType.BUY, "10", "2026-01-01", portfolio = p1),
                trn(TrnType.SPLIT, "2", "2026-02-01", portfolio = p1),
                trn(TrnType.BUY, "3", "2026-01-01", portfolio = p2)
            )

        val summary = TrnTradeSummaryBuilder.build(trns, TrnGroupBy.PORTFOLIO)

        assertThat(qtyFor(summary, "p1")).isEqualByComparingTo("20")
        assertThat(qtyFor(summary, "p2")).isEqualByComparingTo("3")
    }

    @Test
    fun `portfolio grouping nests split-adjusted broker sub-totals`() {
        val dbs = Broker(name = "DBS", owner = owner)
        val scb = Broker(name = "SCB", owner = owner)
        val trns =
            listOf(
                trn(TrnType.BUY, "175", "2024-12-10", portfolio = p1, broker = dbs),
                trn(TrnType.BUY, "80", "2025-12-03", portfolio = p1, broker = scb),
                trn(TrnType.SPLIT, "2", "2026-01-01", portfolio = p1) // portfolio-wide
            )

        val summary = TrnTradeSummaryBuilder.build(trns, TrnGroupBy.PORTFOLIO)

        // Portfolio total = (175 + 80) * 2 = 510.
        assertThat(qtyFor(summary, "p1")).isEqualByComparingTo("510")
        // Each broker sub-total carries the portfolio-wide split: 175*2, 80*2.
        assertThat(subQtyFor(summary, "p1", dbs.id)).isEqualByComparingTo("350")
        assertThat(subQtyFor(summary, "p1", scb.id)).isEqualByComparingTo("160")
    }

    @Test
    fun `broker grouping carries no sub-totals`() {
        val dbs = Broker(name = "DBS", owner = owner)
        val summary =
            TrnTradeSummaryBuilder.build(
                listOf(trn(TrnType.BUY, "10", "2026-01-01", broker = dbs)),
                TrnGroupBy.BROKER
            )

        assertThat(summary.groups.first { it.groupId == dbs.id }.subTotals).isEmpty()
    }

    @Test
    fun `same day split applies after same day buy and before same day sell`() {
        val dbs = Broker(name = "DBS", owner = owner)
        val trns =
            listOf(
                trn(TrnType.SELL, "10", "2026-02-01", broker = dbs),
                trn(TrnType.SPLIT, "2", "2026-02-01"),
                trn(TrnType.BUY, "12", "2026-02-01", broker = dbs)
            )

        val summary = TrnTradeSummaryBuilder.build(trns, TrnGroupBy.BROKER)

        // buy 12 -> split *2 = 24 -> sell 10 = 14
        assertThat(qtyFor(summary, dbs.id)).isEqualByComparingTo("14")
    }
}