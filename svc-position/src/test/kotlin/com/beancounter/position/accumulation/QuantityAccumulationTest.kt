package com.beancounter.position.accumulation

import com.beancounter.common.model.Market
import com.beancounter.common.model.Positions
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.PROP_PURCHASED
import com.beancounter.position.Constants.Companion.PROP_SOLD
import com.beancounter.position.Constants.Companion.PROP_TOTAL
import com.beancounter.position.Constants.Companion.hundred
import org.assertj.core.api.Assertions.assertThat
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
}