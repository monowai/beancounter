package com.beancounter.common.utils

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class TradeCalculatorTest {
    private val quantity: BigDecimal = BigDecimal.TEN
    private val price: BigDecimal = BigDecimal("11.99")
    private val amount: BigDecimal = BigDecimal("119.90")
    private val tradeCalculator: TradeCalculator = TradeCalculator(NumberUtils())

    @Test
    fun amountWithoutFees() {
        val amount = tradeCalculator.amount(quantity, price)
        assertThat(amount).isEqualTo(amount)
    }

    @Test
    fun amountWithFees() {
        val amount = tradeCalculator.amount(quantity, price, BigDecimal("10.01"))
        assertThat(amount).isEqualTo(BigDecimal("129.91"))
    }

    @Test
    fun testAmountFromTrnInputTradeAmount() {
        val trnInput = TrnInput(
            tradeAmount = amount,
            assetId = "any",
            quantity = BigDecimal("99"),
            price = price,
        )
        assertThat(tradeCalculator.amount(trnInput)).isEqualTo(amount)
    }

    @Test
    fun testAmountFromTrnInputWithoutTradeAmount() {
        val trnInput = TrnInput(assetId = "anycode", price = price, quantity = quantity)
        assertThat(tradeCalculator.amount(trnInput)).isEqualTo(amount)
    }

    @Test
    fun testAmountForCorporateAction() {
        val trnInput = TrnInput(
            trnType = TrnType.DIVI,
            tradeAmount = amount,
            quantity = quantity,
            assetId = "any",
            price = price,
        )
        assertThat(tradeCalculator.amount(trnInput)).isEqualTo(amount)
    }
}
