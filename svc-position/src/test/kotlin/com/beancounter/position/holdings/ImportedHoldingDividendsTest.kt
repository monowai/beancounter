package com.beancounter.position.holdings

import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils
import com.beancounter.position.Constants
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.DividendBehaviour
import com.beancounter.position.accumulation.SplitBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.irr.PositionIrr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * An imported holding (an ADD with a cost date) is owed dividends from its cost date. Those
 * dividends are dated before the ADD's trade date, yet accumulate onto the holding: they are
 * its income and IRR cash flows, without changing its quantity or re-opening it.
 */
class ImportedHoldingDividendsTest {
    private val accumulator =
        Accumulator(TrnBehaviourFactory(listOf(BuyBehaviour(), DividendBehaviour(), SplitBehaviour())))
    private val positionService = PositionService(accumulator)
    private val positionIrr = PositionIrr(IrrCalculator(minHoldingDays = 365, dateUtils = DateUtils()))
    private val portfolio = PortfolioUtils.getPortfolio()
    private val asset = AssetUtils.getTestAsset(Constants.US, "IMPD")
    private val costDate = LocalDate.of(2016, 1, 4)
    private val tradeDate = LocalDate.of(2026, 11, 18)
    private val marketValue = BigDecimal("2000")

    private fun add() =
        Trn(
            trnType = TrnType.ADD,
            asset = asset,
            portfolio = portfolio,
            quantity = BigDecimal.TEN,
            tradeDate = tradeDate,
            tradeAmount = BigDecimal("1000"),
            costDate = costDate
        )

    private fun dividend(paid: LocalDate) =
        Trn(
            trnType = TrnType.DIVI,
            asset = asset,
            portfolio = portfolio,
            quantity = BigDecimal.TEN,
            tradeDate = paid,
            tradeAmount = BigDecimal("20"),
            cashAmount = BigDecimal("20")
        )

    // As svc-data returns them: in trade date order, so the dividends come before the ADD.
    private fun build(vararg trns: Trn): Position =
        positionService
            .build(portfolio, PositionRequest(portfolio.id, trns.toList()))
            .data
            .getOrThrow(asset)

    private fun valued(position: Position): Position {
        position.getMoneyValues(Position.In.TRADE).marketValue = marketValue
        position.periodicCashFlows.add(position, tradeDate)
        return position
    }

    @Test
    fun `should accumulate dividends paid before an imported holding's trade date onto it`() {
        val position = build(dividend(LocalDate.of(2018, 6, 1)), dividend(LocalDate.of(2020, 6, 1)), add())

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("10")
        assertThat(position.getMoneyValues(Position.In.TRADE).dividends).isEqualByComparingTo("40")
        assertThat(position.dateValues.opened).isEqualTo(costDate)
        assertThat(position.dateValues.firstTransaction).isEqualTo(tradeDate)
        assertThat(position.dateValues.last).isEqualTo(tradeDate)
        assertThat(position.dateValues.lastDividend).isEqualTo(LocalDate.of(2020, 6, 1))
        assertThat(position.periodicCashFlows.cashFlows).containsExactlyInAnyOrder(
            CashFlow(-1000.0, costDate),
            CashFlow(20.0, LocalDate.of(2018, 6, 1)),
            CashFlow(20.0, LocalDate.of(2020, 6, 1))
        )
    }

    @Test
    fun `should include the dividends in an imported holding's IRR`() {
        val withDividends =
            valued(build(dividend(LocalDate.of(2018, 6, 1)), dividend(LocalDate.of(2020, 6, 1)), add()))
        val without = valued(build(add()))

        val irrWith = positionIrr.ofPosition(withDividends, tradeDate, BigDecimal.ZERO)
        val irrWithout = positionIrr.ofPosition(without, tradeDate, BigDecimal.ZERO)

        assertThat(irrWith).isGreaterThan(irrWithout)
    }

    @Test
    fun `should never apply a split dated before an imported holding's trade date`() {
        val split =
            Trn(
                trnType = TrnType.SPLIT,
                asset = asset,
                quantity = BigDecimal("2"),
                tradeDate = LocalDate.of(2019, 1, 2)
            )
        val position = build(dividend(LocalDate.of(2018, 6, 1)), split, dividend(LocalDate.of(2020, 6, 1)), add())

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("10")
        assertThat(position.getMoneyValues(Position.In.TRADE).dividends).isEqualByComparingTo("40")
    }

    @Test
    fun `should leave a portfolio without cost-dated holdings in trade date order`() {
        val bought = add().copy(trnType = TrnType.BUY, costDate = null, tradeDate = LocalDate.of(2017, 1, 3))
        val position = build(bought, dividend(LocalDate.of(2018, 6, 1)))

        assertThat(position.dateValues.opened).isEqualTo(LocalDate.of(2017, 1, 3))
        assertThat(position.dateValues.firstTransaction).isEqualTo(LocalDate.of(2017, 1, 3))
        assertThat(position.dateValues.lastDividend).isEqualTo(LocalDate.of(2018, 6, 1))
        assertThat(position.getMoneyValues(Position.In.TRADE).dividends).isEqualByComparingTo("20")
    }
}