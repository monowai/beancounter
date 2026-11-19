package com.beancounter.position.irr

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SplitBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A holding recorded today at a cost paid years ago opens, and its IRR runs, from the cost
 * date, while quantity, splits and firstTransaction stay on the trade date.
 */
class PositionIrrCostDateTest {
    private val positionIrr = PositionIrr(IrrCalculator(minHoldingDays = 365, dateUtils = DateUtils()))
    private val accumulator = Accumulator(TrnBehaviourFactory(listOf(BuyBehaviour(), SplitBehaviour())))
    private val asset = AssetUtils.getTestAsset(Constants.US, "BRK.B")
    private val today = LocalDate.of(2026, 11, 18)
    private val heldSince = today.minusYears(10)
    private val cost = BigDecimal("1157.10")
    private val marketValue = BigDecimal("5867.60")

    // 5,867.60 on 1,157.10 is a 407% simple return.
    private val roi = marketValue.subtract(cost).divide(cost, 4, RoundingMode.HALF_UP)

    private fun add(
        costDate: LocalDate?,
        trnType: TrnType = TrnType.ADD
    ) = Trn(
        trnType = trnType,
        asset = asset,
        quantity = BigDecimal.TEN,
        tradeDate = today,
        tradeAmount = cost,
        costDate = costDate
    )

    private fun valued(
        costDate: LocalDate?,
        trnType: TrnType = TrnType.ADD
    ): Position {
        val position = accumulator.accumulate(add(costDate, trnType), Positions())
        position.getMoneyValues(Position.In.TRADE).marketValue = marketValue
        position.periodicCashFlows.add(position, today)
        return position
    }

    @Test
    fun `should annualise a holding recorded today with a cost date ten years ago`() {
        val irr = positionIrr.ofPosition(valued(heldSince), today, roi)

        assertThat(irr.toDouble()).isCloseTo(0.176, within(0.005))
    }

    @Test
    fun `should annualise the portfolio's combined cash flows from the cost date`() {
        val portfolio = Positions()
        portfolio.periodicCashFlows.addAll(valued(heldSince).periodicCashFlows.cashFlows)

        assertThat(positionIrr.of(portfolio.periodicCashFlows).toDouble()).isCloseTo(0.176, within(0.005))
    }

    @Test
    fun `should report the simple return of a holding recorded today with no cost date`() {
        val irr = positionIrr.ofPosition(valued(null), today, roi)

        assertThat(irr).isCloseTo(BigDecimal("4.071"), within(BigDecimal("0.0001")))
    }

    @Test
    fun `should report the simple return of a buy recorded today, whatever cost date it carries`() {
        val irr = positionIrr.ofPosition(valued(heldSince, TrnType.BUY), today, roi)

        assertThat(irr).isCloseTo(roi, within(BigDecimal("0.0001")))
    }

    @Test
    fun `should open on the cost date and keep firstTransaction and splits on the trade date`() {
        val withCostDate = Positions()
        val without = Positions()
        val dated = accumulator.accumulate(add(heldSince), withCostDate)
        val undated = accumulator.accumulate(add(null), without)
        val split = Trn(trnType = TrnType.SPLIT, asset = asset, quantity = BigDecimal("2"), tradeDate = today)
        accumulator.accumulate(split, withCostDate)
        accumulator.accumulate(split.copy(), without)

        assertThat(dated.dateValues.opened).isEqualTo(heldSince)
        assertThat(undated.dateValues.opened).isEqualTo(today)
        assertThat(dated.dateValues.firstTransaction).isEqualTo(today)
        assertThat(undated.dateValues.firstTransaction).isEqualTo(today)
        assertThat(dated.quantityValues.getTotal()).isEqualByComparingTo("20")
        assertThat(dated.quantityValues.getTotal()).isEqualByComparingTo(undated.quantityValues.getTotal())
    }
}