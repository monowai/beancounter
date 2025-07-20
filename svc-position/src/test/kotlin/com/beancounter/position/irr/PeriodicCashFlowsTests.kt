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
    fun `cash flows are not cleared after full sell out (broken logic) using Accumulator`() {
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
        // Buy again on day 20
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
        // Expecting 1 cash flows - the last buy after we sold out.
        assertEquals(1, position.periodicCashFlows.cashFlows.size)
    }
}