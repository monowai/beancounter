package com.beancounter.position.irr

import com.beancounter.common.model.CashFlow
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.position.Constants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
                CashFlow(100.0, LocalDate.of(2022, 1, 1)),
                CashFlow(200.0, LocalDate.of(2022, 1, 2)),
                CashFlow(300.0, LocalDate.of(2022, 1, 3)),
            )

        val cashFlows2 =
            listOf(
                CashFlow(400.0, LocalDate.of(2022, 1, 1)),
                CashFlow(500.0, LocalDate.of(2022, 1, 2)),
                CashFlow(600.0, LocalDate.of(2022, 1, 3)),
            )

        periodicCashFlows.addAll(cashFlows1)
        periodicCashFlows.addAll(cashFlows2)

        val expectedCashFlows =
            listOf(
                CashFlow(500.0, LocalDate.of(2022, 1, 1)),
                CashFlow(700.0, LocalDate.of(2022, 1, 2)),
                CashFlow(900.0, LocalDate.of(2022, 1, 3)),
            )

        assertEquals(expectedCashFlows, periodicCashFlows.cashFlows)
    }

    @Test
    fun `cash-flowFromTrn when no cashEffect`() {
        val periodicCashFlows = PeriodicCashFlows()
        periodicCashFlows.add(
            Trn(
                asset = AssetUtils.getTestAsset(Constants.US, "CODE"),
                trnType = TrnType.BUY,
                tradeAmount = Constants.twoK,
            ),
        )
        assertThat(periodicCashFlows.cashFlows[0].amount).isEqualTo(Constants.twoK.negate().toDouble())
    }
}
