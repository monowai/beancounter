package com.beancounter.position.irr

import com.beancounter.common.model.PeriodicCashFlows
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.position.Constants.Companion.US
import org.junit.jupiter.api.Test
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.LocalDate

class IrrCalculatorSimpleRoiTest {
    private val asset = AssetUtils.getTestAsset(US, "TestAsset")

    @Test
    fun `calculateSimpleROI is called when holding period is less than custom minHoldingDays`() {
        val customMinHoldingDays = 500
        val irrCalculator = spy(IrrCalculator(minHoldingDays = customMinHoldingDays))
        val periodicCashFlows =
            PeriodicCashFlows().apply {
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now(),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal(-1000)
                    )
                )
                add(
                    Trn(
                        asset = asset,
                        tradeDate = LocalDate.now().plusDays(10),
                        trnType = TrnType.BUY,
                        cashAmount = BigDecimal(1100)
                    )
                )
            }

        irrCalculator.calculate(periodicCashFlows)

        verify(irrCalculator).calculateSimpleRoi(periodicCashFlows)
    }
}