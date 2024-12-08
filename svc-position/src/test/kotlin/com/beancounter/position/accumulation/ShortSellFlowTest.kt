package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.twoK
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ShortSellFlowTest {
    private val buyBehaviour = BuyBehaviour()
    private val sellBehaviour = SellBehaviour()
    private val asset =
        Asset(
            "TEST",
            market = Constants.US,
        )
    private val fifty = BigDecimal("50.0")

    @Test
    fun testShortSellFlow() {
        val positions = Positions()
        // Given I have a starting position of 50
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = hundred,
                cashAmount = hundred.negate(),
            )
        val initialPosition =
            buyBehaviour.accumulate(
                buyTrn,
                positions,
            )
        val twoHundreds = BigDecimal("200")
        // When I sell 50
        val sellTrn1 =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = fifty,
                tradeAmount = twoHundreds,
                cashAmount = twoHundreds,
            )
        sellBehaviour.accumulate(
            sellTrn1,
            positions,
            initialPosition,
        )
        assertEquals(
            BigDecimal.ZERO,
            initialPosition.quantityValues.getTotal(),
        )
        assertEquals(
            0,
            initialPosition.moneyValues[Position.In.TRADE]!!
                .realisedGain
                .compareTo(BigDecimal("100")),
        )

        // And I sell 50 again
        val sellTrn2 =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = fifty,
                tradeAmount = twoK,
                cashAmount = twoK,
            )
        sellBehaviour.accumulate(
            sellTrn2,
            positions,
            initialPosition,
        )
        assertEquals(
            fifty.negate(),
            initialPosition.quantityValues.getTotal(),
        )
        // And then I buy 50
        val buyTrn2 =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = hundred,
            )
        buyBehaviour.accumulate(
            buyTrn2,
            positions,
            initialPosition,
        )

        // Then I have a position of 0 and a profit or loss
        assertEquals(
            0,
            initialPosition.quantityValues.getTotal().compareTo(BigDecimal.ZERO),
        )
        assertEquals(
            BigDecimal("2100.00"),
            initialPosition.moneyValues[Position.In.TRADE]!!.realisedGain,
        )
        // Add assertions for profit or loss as needed
    }
}
