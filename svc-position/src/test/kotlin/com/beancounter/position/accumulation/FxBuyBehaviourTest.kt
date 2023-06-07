package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.nzdCashBalance
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Buy the trade asset, sell the cash asset.
 */
@SpringBootTest(classes = [Accumulator::class])
class FxBuyBehaviourTest {

    @Autowired
    private lateinit var accumulator: Accumulator

    @Test
    fun is_FxBuyAccumulated() {
        val trn = Trn(
            trnType = TrnType.FX_BUY,
            asset = usdCashBalance,
            quantity = BigDecimal("2500.00"), // Buy
            cashAsset = nzdCashBalance,
            cashCurrency = NZD,
            tradeCashRate = BigDecimal("1.5"), // Sell
            cashAmount = BigDecimal("-5000.00"),
        )
        val positions = Positions()
        val usdPosition = accumulator.accumulate(trn, positions)
        // Fx affects two positions
        assertThat(positions.positions).containsKeys(toKey(usdCashBalance), toKey(nzdCashBalance))
        // Primary Position
        assertThat(usdPosition.asset).isEqualTo(usdCashBalance)
        assertThat(usdPosition.quantityValues)
            .hasFieldOrPropertyWithValue("total", trn.tradeAmount)
        assertThat(usdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue("costBasis", trn.quantity)
            .hasFieldOrPropertyWithValue("costValue", trn.quantity)
            .hasFieldOrPropertyWithValue("marketValue", BigDecimal.ZERO) // Not yet valued

        val nzdPosition = positions.positions[toKey(nzdCashBalance)]
        assertThat(nzdPosition!!.quantityValues)
            .hasFieldOrPropertyWithValue("total", trn.cashAmount)
        assertThat(nzdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue("costBasis", trn.cashAmount)
            .hasFieldOrPropertyWithValue("costValue", trn.cashAmount)
            .hasFieldOrPropertyWithValue("marketValue", BigDecimal.ZERO) // Not yet valued
    }
}
