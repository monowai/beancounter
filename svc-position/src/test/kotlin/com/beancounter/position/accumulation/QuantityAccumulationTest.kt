package com.beancounter.position.accumulation

import com.beancounter.common.model.Market
import com.beancounter.common.model.Positions
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
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
    private val hundred: BigDecimal = BigDecimal("100")
    private val zero: BigDecimal = BigDecimal(0)
    private val twoHundred = BigDecimal(200)

    @Autowired
    private lateinit var accumulator: Accumulator

    @Test
    fun is_QuantityPrecision() {
        val quantityValues = QuantityValues()
        // Null total
        assertThat(quantityValues.getPrecision()).isEqualTo(0)
        quantityValues.purchased = BigDecimal("100.9992")
        assertThat(quantityValues.getTotal()).isEqualTo("100.9992")
        assertThat(quantityValues.getPrecision()).isEqualTo(3)
        quantityValues.purchased = hundred
        assertThat(quantityValues.getPrecision()).isEqualTo(0)

        // User defined precision
        quantityValues.setPrecision(40)
        assertThat(quantityValues.getPrecision()).isEqualTo(40)
    }

    @Test
    fun is_TotalQuantityCorrect() {
        val positions = Positions(getPortfolio())
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = getTestAsset(Market("marketCode"), "CODE"),
                quantity = hundred,
                tradeAmount = BigDecimal(2000),
            )
        assertThat(accumulator.accumulate(buyTrn, positions).quantityValues)
            .hasFieldOrPropertyWithValue(P_TOTAL, hundred)
            .hasFieldOrPropertyWithValue(P_PURCHASED, hundred)

        assertThat(accumulator.accumulate(buyTrn, positions).quantityValues)
            .hasFieldOrPropertyWithValue(P_PURCHASED, twoHundred)
            .hasFieldOrPropertyWithValue(P_SOLD, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(P_TOTAL, twoHundred)
        // Sell to zero
        val sell = Trn(trnType = TrnType.SELL, asset = buyTrn.asset, quantity = hundred)
        // Track the money
        assertThat(accumulator.accumulate(sell, positions).quantityValues)
            .hasFieldOrPropertyWithValue(P_SOLD, BigDecimal(-100))
            .hasFieldOrPropertyWithValue(P_PURCHASED, twoHundred)
            .hasFieldOrPropertyWithValue(P_TOTAL, hundred)
        // But reset the quantities
        assertThat(accumulator.accumulate(sell, positions).quantityValues)
            .hasFieldOrPropertyWithValue(P_SOLD, zero)
            .hasFieldOrPropertyWithValue(P_PURCHASED, zero)
            .hasFieldOrPropertyWithValue(P_TOTAL, zero)
    }

    companion object {
        const val P_SOLD = "sold"
        const val P_PURCHASED = "purchased"
        const val P_TOTAL = "total"
    }
}
