package com.beancounter.position.irr

import IrrCalculator
import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.US
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private const val START_DATE = "2024-01-01"

/**
 * validate various cash flow scenarios.
 */
class IrrCalculatorTests {
    private val irrService = IrrCalculator()
    private val initialCost = -1000.0
    private val marketValue = 1100.0
    private val asset =
        AssetUtils.getTestAsset(
            US,
            "AnyAsset"
        )
    private val dateUtils = DateUtils()

    @Test
    fun `noCash-flows_don't fail`() {
        testCalculateIRR(
            listOf(),
            0.0
        )
    }

    @Test
    fun testCalculateIRR_Annual() {
        testCalculateIRR(
            listOf(
                "2019-01-01" to initialCost,
                "2020-01-01" to 300.0,
                "2021-01-01" to 420.0,
                "2022-01-01" to 680.0,
                "2023-01-01" to marketValue
            ),
            0.379
        )
    }

    @Test
    fun testCalculateIRR_EdgeCases() {
        testCalculateIRR(
            listOf(
                "2015-06-23" to -2298.00,
                "2017-08-21" to 2431.5,
                "2020-02-27" to 1612.98,
                "2023-06-08" to -2714.09,
                "2024-05-22" to 3822.34
            ),
            0.221
        )
    }

    @Test
    fun testCalculateIRR_QuarterlyDividends() {
        testCalculateIRR(
            listOf(
                "2023-01-01" to initialCost,
                "2023-04-10" to 50.0,
                "2023-07-01" to 50.0,
                "2023-10-01" to 50.0,
                START_DATE to marketValue
            ),
            .269
        )
    }

    @Test
    fun testCalculateIRR_Dividend() {
        testCalculateIRR(
            listOf(
                START_DATE to initialCost,
                "2024-03-01" to 50.0,
                "2025-07-01" to marketValue
            ),
            0.102156
        )
    }

    @Test
    fun testCalculateIRR_ShortHoldingPeriod() {
        testCalculateIRR(
            listOf(
                START_DATE to initialCost,
                "2024-01-05" to marketValue
            ),
            .1
        )
    }

    @Test
    fun testCalculateIRR_NoMarketOrCostValue() {
        testCalculateIRR(
            listOf(
                START_DATE to 0.0,
                "2024-01-05" to 0.0
            ),
            0.0 // Not sure if this is correct
        )
    }

    @Test
    fun testCalculateIRR_NoMarketValue() {
        testCalculateIRR(
            listOf(
                START_DATE to initialCost,
                "2024-01-05" to 0.0
            ),
            -1.0 // Not sure if this is correct
        )
    }

    @Test
    fun testCalculateIRR_NoCost() {
        testCalculateIRR(
            listOf(
                START_DATE to 0.0,
                "2024-01-05" to marketValue
            ),
            0.0
        )
    }

    @Test
    fun testCalculateIRR_ShortHoldingPeriodSingleDividend() {
        testCalculateIRR(
            listOf(
                "2023-01-01" to initialCost,
                "2023-12-31" to 100.0,
                START_DATE to 900.00
            ),
            0.0
        )
    }

    @Test
    fun testCalculateIRR_LossShortTermHolding() {
        testCalculateIRR(
            listOf(
                START_DATE to initialCost,
                "2024-01-10" to marketValue - 400
            ),
            -0.3
        )
    }

    private fun testCalculateIRR(
        cashFlows: List<Pair<String, Double>>,
        expectedIrr: Double
    ) {
        val periodicCashFlows = getPeriodicCashFlows(cashFlows)
        val irr = irrService.calculate(periodicCashFlows)
        assertEquals(
            expectedIrr,
            irr,
            0.001
        )
    }

    private fun getPeriodicCashFlows(cashFlows: List<Pair<String, Double>>): PeriodicCashFlows {
        val periodicCashFlows = PeriodicCashFlows()
        cashFlows.forEach { (date, value) ->
            periodicCashFlows.add(
                Trn(
                    asset = asset,
                    tradeDate = dateUtils.getDate(date),
                    trnType = TrnType.BUY,
                    cashAmount = BigDecimal(value)
                )
            )
        }
        return periodicCashFlows
    }
}