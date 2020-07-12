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
internal class TestAccumulationOfQuantityValues {
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
        quantityValues.purchased = BigDecimal("100")
        assertThat(quantityValues.getPrecision()).isEqualTo(0)

        // User defined precision
        quantityValues.setPrecision(40)
        assertThat(quantityValues.getPrecision()).isEqualTo(40)
    }

    @Test
    fun is_TotalQuantityCorrect() {
        val buyTrn = Trn(TrnType.BUY, getAsset("marketCode", "CODE"), BigDecimal(100))
        buyTrn.tradeAmount = BigDecimal(2000)
        var position = Position(buyTrn.asset)
        assertThat(position.quantityValues)
                .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO)
        val portfolio = getPortfolio("TEST")
        position = accumulator.accumulate(buyTrn, portfolio, position)
        assertThat(position.quantityValues)
                .hasFieldOrPropertyWithValue("purchased", BigDecimal(100))
                .hasFieldOrPropertyWithValue("total", BigDecimal(100))
        position = accumulator.accumulate(buyTrn, portfolio, position)
        assertThat(position.quantityValues)
                .hasFieldOrPropertyWithValue("purchased", BigDecimal(200))
                .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
                .hasFieldOrPropertyWithValue("total", BigDecimal(200))
        val sell = Trn(TrnType.SELL, buyTrn.asset, BigDecimal(100))
        position = accumulator.accumulate(sell, portfolio, position)
        assertThat(position.quantityValues)
                .hasFieldOrPropertyWithValue("sold", BigDecimal(-100))
                .hasFieldOrPropertyWithValue("purchased", BigDecimal(200))
                .hasFieldOrPropertyWithValue("total", BigDecimal(100))
        position = accumulator.accumulate(sell, portfolio, position)
        assertThat(position.quantityValues)
                .hasFieldOrPropertyWithValue("sold", BigDecimal(-200))
                .hasFieldOrPropertyWithValue("purchased", BigDecimal(200))
                .hasFieldOrPropertyWithValue("total", BigDecimal(0))
    }
}