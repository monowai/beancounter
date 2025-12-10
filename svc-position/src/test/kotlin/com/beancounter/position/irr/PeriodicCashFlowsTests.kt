package com.beancounter.position.irr

import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.position.Constants
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Test class for PeriodicCashFlows.
 */
class PeriodicCashFlowsTests {
    @Test
    fun testAddAll() {
        val periodicCashFlows = PeriodicCashFlows()

        val cashFlows1 =
            listOf(
                CashFlow(
                    100.0,
                    LocalDate.of(
                        2022,
                        1,
                        1
                    )
                ),
                CashFlow(
                    200.0,
                    LocalDate.of(
                        2022,
                        1,
                        2
                    )
                ),
                CashFlow(
                    300.0,
                    LocalDate.of(
                        2022,
                        1,
                        3
                    )
                )
            )

        val cashFlows2 =
            listOf(
                CashFlow(
                    400.0,
                    LocalDate.of(
                        2022,
                        1,
                        1
                    )
                ),
                CashFlow(
                    500.0,
                    LocalDate.of(
                        2022,
                        1,
                        2
                    )
                ),
                CashFlow(
                    600.0,
                    LocalDate.of(
                        2022,
                        1,
                        3
                    )
                )
            )

        periodicCashFlows.addAll(cashFlows1)
        periodicCashFlows.addAll(cashFlows2)

        val expectedCashFlows =
            listOf(
                CashFlow(
                    500.0,
                    LocalDate.of(
                        2022,
                        1,
                        1
                    )
                ),
                CashFlow(
                    700.0,
                    LocalDate.of(
                        2022,
                        1,
                        2
                    )
                ),
                CashFlow(
                    900.0,
                    LocalDate.of(
                        2022,
                        1,
                        3
                    )
                )
            )

        assertEquals(
            expectedCashFlows,
            periodicCashFlows.cashFlows
        )
    }

    @Test
    fun `cash-flowFromTrn when no cashEffect`() {
        val periodicCashFlows = PeriodicCashFlows()
        periodicCashFlows.add(
            Trn(
                asset =
                    AssetUtils.getTestAsset(
                        Constants.US,
                        "CODE"
                    ),
                trnType = TrnType.BUY,
                tradeAmount = Constants.twoK
            )
        )
        assertThat(periodicCashFlows.cashFlows[0].amount).isEqualTo(
            Constants.twoK.negate().toDouble()
        )
    }

    @Test
    fun `cash flows are preserved after full sell out for XIRR calculation`() {
        val accumulator = Accumulator(TrnBehaviourFactory(listOf(BuyBehaviour(), SellBehaviour())))
        val asset = AssetUtils.getTestAsset(Constants.US, "TEST")
        val positions = Positions()
        // Buy 1000 on day 1
        accumulator.accumulate(
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = BigDecimal(10),
                tradeDate = LocalDate.of(2024, 1, 1),
                tradeAmount = BigDecimal(1000),
                cashAmount = BigDecimal(-1000)
            ),
            positions
        )
        // Sell 1000 on day 10 (fully close)
        val soldOutPosition =
            accumulator.accumulate(
                Trn(
                    asset = asset,
                    trnType = TrnType.SELL,
                    quantity = BigDecimal(-10),
                    tradeDate = LocalDate.of(2024, 1, 10),
                    tradeAmount = BigDecimal(-1000),
                    cashAmount = BigDecimal(1100)
                ),
                positions
            )

        // Position quantity should be 0
        assertThat(soldOutPosition.quantityValues.getTotal().compareTo(BigDecimal.ZERO)).isEqualTo(0)
        // Cash flows should be preserved (2 flows: buy and sell) for XIRR calculation
        assertEquals(2, soldOutPosition.periodicCashFlows.cashFlows.size)
        // Opened date is preserved for UI display (shows holding period even when sold out)
        assertThat(soldOutPosition.dateValues.opened).isEqualTo(LocalDate.of(2024, 1, 1))
        // firstTransaction should be preserved
        assertThat(soldOutPosition.dateValues.firstTransaction).isEqualTo(LocalDate.of(2024, 1, 1))
    }

    @Test
    fun `cash flows are cleared when position is reopened after sell out - new investment cycle`() {
        val accumulator = Accumulator(TrnBehaviourFactory(listOf(BuyBehaviour(), SellBehaviour())))
        val asset = AssetUtils.getTestAsset(Constants.US, "TEST")
        val positions = Positions()
        // Buy 1000 on day 1
        accumulator.accumulate(
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = BigDecimal(10),
                tradeDate = LocalDate.of(2024, 1, 1),
                tradeAmount = BigDecimal(1000),
                cashAmount = BigDecimal(-1000)
            ),
            positions
        )
        // Sell 1000 on day 10 (fully close) - this completes the investment cycle
        accumulator.accumulate(
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = BigDecimal(-10),
                tradeDate = LocalDate.of(2024, 1, 10),
                tradeAmount = BigDecimal(-1000),
                cashAmount = BigDecimal(1100)
            ),
            positions
        )
        // Buy again on day 20 (reopen position) - this starts a NEW investment cycle
        val position =
            accumulator.accumulate(
                Trn(
                    asset = asset,
                    trnType = TrnType.BUY,
                    quantity = BigDecimal(10),
                    tradeDate = LocalDate.of(2024, 1, 20),
                    tradeAmount = BigDecimal(500),
                    cashAmount = BigDecimal(-500)
                ),
                positions
            )

        // Quantity should be 10 after the last buy
        assertEquals(BigDecimal(10), position.quantityValues.getTotal())
        // Only 1 cash flow should exist (the new buy) - historical cash flows are cleared on reopening
        // because the previous investment cycle is complete and realized
        assertEquals(1, position.periodicCashFlows.cashFlows.size)
        // The cash flow should be from the new buy
        assertThat(position.periodicCashFlows.cashFlows[0].date).isEqualTo(LocalDate.of(2024, 1, 20))
        // Opened date should be set to reopening date (new holding period)
        assertThat(position.dateValues.opened).isEqualTo(LocalDate.of(2024, 1, 20))
        // firstTransaction should NEVER change - it tracks the very first transaction ever for this asset
        assertThat(position.dateValues.firstTransaction).isEqualTo(LocalDate.of(2024, 1, 1))
    }
}