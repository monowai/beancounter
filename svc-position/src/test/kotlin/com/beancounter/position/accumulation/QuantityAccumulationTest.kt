package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.common.model.Positions
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.PROP_PURCHASED
import com.beancounter.position.Constants.Companion.PROP_SOLD
import com.beancounter.position.Constants.Companion.PROP_TOTAL
import com.beancounter.position.Constants.Companion.hundred
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Quantity Accumulation tests
 */
@SpringBootTest(classes = [Accumulator::class])
internal class QuantityAccumulationTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    companion object {
        private const val CUSTOM_PRECISION = 40
    }

    @Test
    fun `Verify quantity precision handling`() {
        val quantityValues = QuantityValues()

        // Verify initial precision should be zero when no quantities are defined
        assertThat(quantityValues.getPrecision()).isEqualTo(0)

        // Set purchased quantity with a decimal and verify precision calculation
        val decimalQuantity = BigDecimal("100.9992")
        quantityValues.purchased = decimalQuantity
        assertThat(quantityValues.getTotal()).isEqualTo(decimalQuantity)
        assertThat(quantityValues.getPrecision()).isEqualTo(3) // Precision of "100.9992" is 3

        // Reset purchased to a whole number and verify precision resets to zero
        val wholeNumberQuantity = BigDecimal(100)
        quantityValues.purchased = wholeNumberQuantity
        assertThat(quantityValues.getPrecision()).isEqualTo(
            0
        ) // Whole numbers have zero decimal places

        // Setting and verifying a user-defined precision level
        quantityValues.setPrecision(CUSTOM_PRECISION)
        assertThat(quantityValues.getPrecision()).isEqualTo(CUSTOM_PRECISION)
    }

    @Test
    fun `Verify total quantity after transactions`() {
        val positions = Positions(getPortfolio())
        val asset =
            getTestAsset(
                Market("marketCode"),
                "CODE"
            )
        val twoHundred = BigDecimal(200)

        // Perform the first buy transaction
        performAndAssertTransaction(
            positions,
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal(2000)
            ),
            expectedTotal = hundred,
            expectedPurchased = hundred,
            expectedSold = BigDecimal.ZERO
        )

        // Double the position
        performAndAssertTransaction(
            positions,
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal(2000)
            ),
            expectedTotal = twoHundred,
            expectedPurchased = twoHundred,
            expectedSold = BigDecimal.ZERO
        )

        // Perform a sell transaction to halve the position
        performAndAssertTransaction(
            positions,
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal(2000)
            ),
            expectedTotal = hundred,
            expectedPurchased = twoHundred,
            expectedSold = BigDecimal(-100)
        )

        // Sell down the entire position effectively cleaning
        performAndAssertTransaction(
            positions,
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal(2000)
            )
        )
    }

    private fun performAndAssertTransaction(
        positions: Positions,
        transaction: Trn,
        expectedTotal: BigDecimal = BigDecimal.ZERO,
        expectedPurchased: BigDecimal = BigDecimal.ZERO,
        expectedSold: BigDecimal = BigDecimal.ZERO
    ) {
        val result =
            accumulator.accumulate(
                transaction,
                positions
            )
        assertThat(result.quantityValues).hasFieldOrPropertyWithValue(
            PROP_TOTAL,
            expectedTotal
        )
        assertThat(result.quantityValues).hasFieldOrPropertyWithValue(
            PROP_PURCHASED,
            expectedPurchased
        )
        assertThat(result.quantityValues).hasFieldOrPropertyWithValue(
            PROP_SOLD,
            expectedSold
        )
    }

    @Test
    fun `should update last date on every transaction`() {
        val asset = getTestAsset(Market("ASX"), "BUY")
        val dateUtils = DateUtils()
        val firstTradeDate = dateUtils.getFormattedDate("2023-06-15")
        val secondTradeDate = dateUtils.getFormattedDate("2023-06-20")
        val positions = Positions(getPortfolio())

        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = firstTradeDate,
                quantity = hundred
            )

        val position = accumulator.accumulate(buyTrn, positions)
        assertThat(position.dateValues.last).isEqualTo(firstTradeDate)

        val sellTrn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                tradeDate = secondTradeDate,
                quantity = BigDecimal("50")
            )

        accumulator.accumulate(sellTrn, positions)
        assertThat(position.dateValues.last).isEqualTo(secondTradeDate)
    }

    @Test
    fun `should enforce sequential date validation`() {
        val asset = getTestAsset(Market("ASX"), "DATE_TEST")
        val dateUtils = DateUtils()
        val firstTradeDate = dateUtils.getFormattedDate("2023-06-15")
        val earlierTradeDate = dateUtils.getFormattedDate("2023-06-10")
        val positions = Positions(getPortfolio())

        val firstTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = firstTradeDate,
                quantity = hundred
            )

        accumulator.accumulate(firstTrn, positions)

        val earlierTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = earlierTradeDate,
                quantity = BigDecimal("50")
            )

        assertThatThrownBy {
            accumulator.accumulate(earlierTrn, positions)
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("Date sequence problem")
    }

    @Test
    fun `should set first transaction date once and never change it`() {
        val asset = getTestAsset(Market("ASX"), "FIRST_TRN")
        val dateUtils = DateUtils()
        val firstTradeDate = dateUtils.getFormattedDate("2023-06-15")
        val laterTradeDate = dateUtils.getFormattedDate("2023-06-20")
        val positions = Positions(getPortfolio())

        // First transaction
        val firstTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = firstTradeDate,
                quantity = hundred
            )

        val position = accumulator.accumulate(firstTrn, positions)
        assertThat(position.dateValues.firstTransaction).isEqualTo(firstTradeDate)

        // Second transaction - firstTransaction should not change
        val secondTrn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                tradeDate = laterTradeDate,
                quantity = BigDecimal("50")
            )

        accumulator.accumulate(secondTrn, positions)
        assertThat(position.dateValues.firstTransaction).isEqualTo(firstTradeDate) // Should remain unchanged
        assertThat(position.dateValues.last).isEqualTo(laterTradeDate) // Should update
    }

    @Test
    fun `opened date should reset after complete sellout and reentry`() {
        // Core scenario: BUY, SELL (all shares), BUY again
        // The opened date should reset to the second BUY date
        val asset = getTestAsset(Market("USD"), "SELLOUT_REENTRY")
        val dateUtils = DateUtils()
        val firstBuyDate = dateUtils.getFormattedDate("2024-06-03")
        val sellDate = dateUtils.getFormattedDate("2024-10-03")
        val secondBuyDate = dateUtils.getFormattedDate("2025-06-13")
        val positions = Positions(getPortfolio())

        // Initial BUY: 30.00 shares
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = firstBuyDate,
                quantity = BigDecimal("30.00")
            )
        val position = accumulator.accumulate(buyTrn, positions)
        assertThat(position.dateValues.opened).isEqualTo(firstBuyDate)

        // SELL: All 30.00 shares (position goes to zero)
        val sellTrn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                tradeDate = sellDate,
                quantity = BigDecimal("30.00")
            )
        accumulator.accumulate(sellTrn, positions)
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)

        // BUY: Re-enter with 15.00 shares
        val reentryTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = secondBuyDate,
                quantity = BigDecimal("15.00")
            )
        accumulator.accumulate(reentryTrn, positions)

        // The opened date should now be the second buy date, not the first buy date
        assertThat(position.dateValues.opened).isEqualTo(secondBuyDate)
    }

    @Test
    fun `should preserve first transaction date after complete sellout and reentry`() {
        val asset = getTestAsset(Market("ASX"), "REENTRY")
        val dateUtils = DateUtils()
        val firstTradeDate = dateUtils.getFormattedDate("2023-06-15")
        val sellDate = dateUtils.getFormattedDate("2023-06-20")
        val reentryDate = dateUtils.getFormattedDate("2023-06-25")
        val positions = Positions(getPortfolio())

        // Initial buy
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = firstTradeDate,
                quantity = hundred
            )

        val position = accumulator.accumulate(buyTrn, positions)
        assertThat(position.dateValues.firstTransaction).isEqualTo(firstTradeDate)
        position.dateValues.opened

        // Sell everything
        val sellTrn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                tradeDate = sellDate,
                quantity = hundred
            )

        accumulator.accumulate(sellTrn, positions)
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)

        // Re-enter position
        val reentryTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                tradeDate = reentryDate,
                quantity = BigDecimal("75")
            )

        accumulator.accumulate(reentryTrn, positions)

        // firstTransaction should remain the original date
        assertThat(position.dateValues.firstTransaction).isEqualTo(firstTradeDate)
        // opened should be reset to the reentry date after complete sellout
        assertThat(position.dateValues.opened).isEqualTo(reentryDate)
        // last should be updated to the reentry date
        assertThat(position.dateValues.last).isEqualTo(reentryDate)
    }
}