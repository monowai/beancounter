package com.beancounter.position

import com.beancounter.common.model.Position
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.service.Accumulator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest(classes = [Accumulator::class])
/**
 * Quantity Accumulation tests
 */
internal class TestAccumulationOfQuantityValues {
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
        val buyTrn = Trn(TrnType.BUY, getAsset("marketCode", "CODE"), hundred)
        buyTrn.tradeAmount = BigDecimal(2000)
        var position = Position(buyTrn.asset)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalProp, BigDecimal.ZERO)
        val portfolio = getPortfolio()
        position = accumulator.accumulate(buyTrn, portfolio, position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(purchasedProp, hundred)
            .hasFieldOrPropertyWithValue(totalProp, hundred)
        position = accumulator.accumulate(buyTrn, portfolio, position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(purchasedProp, twoHundred)
            .hasFieldOrPropertyWithValue(soldProp, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(totalProp, twoHundred)
        // Sell to zero
        val sell = Trn(TrnType.SELL, buyTrn.asset, hundred)
        position = accumulator.accumulate(sell, portfolio, position)
        // Track the money
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(soldProp, BigDecimal(-100))
            .hasFieldOrPropertyWithValue(purchasedProp, twoHundred)
            .hasFieldOrPropertyWithValue(totalProp, hundred)
        position = accumulator.accumulate(sell, portfolio, position)
        // But reset the quantities
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(soldProp, zero)
            .hasFieldOrPropertyWithValue(purchasedProp, zero)
            .hasFieldOrPropertyWithValue(totalProp, zero)
    }

    companion object {
        const val soldProp = "sold"
        const val purchasedProp = "purchased"
        const val totalProp = "total"
    }
}
