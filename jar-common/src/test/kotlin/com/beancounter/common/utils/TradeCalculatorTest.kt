package com.beancounter.common.utils

import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private const val ANY = "any"

/**
 * Unit tests for the {@link TradeCalculator} class which handles computations related to trading calculations,
 * such as determining the trade amount based on quantity and price, including optional fees.
 *
 * <p>This class tests various scenarios to ensure that the TradeCalculator accurately computes
 * the total trading amounts under different conditions. It validates both basic trade amount calculations
 * and those involving additional fees. It also checks the robustness of the calculator when
 * dealing with different types of transaction inputs, including those from corporate actions.</p>
 *
 * <p>Key Test Scenarios:</p>
 * <ul>
 *   <li>Validating the trade amount computation without any additional fees.</li>
 *   <li>Computing the trade amount when additional fees are included.</li>
 *   <li>Verifying the computation from a transaction input directly specifying the trade amount.</li>
 *   <li>Testing the amount computation when only price and quantity are given, without an explicit trade amount.</li>
 *   <li>Ensuring correct calculations for corporate actions where dividends might affect the trade amount.</li>
 * </ul>
 *
 * <p>These tests are essential for confirming the reliability and accuracy of financial calculations
 * within the trading system, especially to ensure that the computed values adhere to expected business rules
 * and financial regulations.</p>
 */
internal class TradeCalculatorTest {
    private val quantity: BigDecimal = BigDecimal.TEN
    private val price: BigDecimal = BigDecimal("11.99")
    private val amount: BigDecimal = BigDecimal("119.90")
    private val tradeCalculator: TradeCalculator = TradeCalculator(NumberUtils())

    @Test
    fun amountWithoutFees() {
        val amount =
            tradeCalculator.amount(
                quantity,
                price
            )
        assertThat(amount).isEqualTo(amount)
    }

    @Test
    fun amountWithFees() {
        val amount =
            tradeCalculator.amount(
                quantity,
                price,
                BigDecimal("10.01")
            )
        assertThat(amount).isEqualTo(BigDecimal("129.91"))
    }

    @Test
    fun testAmountFromTrnInputTradeAmount() {
        val trnInput =
            TrnInput(
                tradeAmount = amount,
                assetId = ANY,
                quantity = BigDecimal("99"),
                price = price
            )
        assertThat(tradeCalculator.amount(trnInput)).isEqualTo(amount)
    }

    @Test
    fun testAmountFromTrnInputWithoutTradeAmount() {
        val trnInput =
            TrnInput(
                assetId = ANY,
                price = price,
                quantity = quantity
            )
        assertThat(tradeCalculator.amount(trnInput)).isEqualTo(amount)
    }

    @Test
    fun testAmountForCorporateAction() {
        val trnInput =
            TrnInput(
                trnType = TrnType.DIVI,
                tradeAmount = amount,
                quantity = quantity,
                assetId = ANY,
                price = price
            )
        assertThat(tradeCalculator.amount(trnInput)).isEqualTo(amount)
    }
}