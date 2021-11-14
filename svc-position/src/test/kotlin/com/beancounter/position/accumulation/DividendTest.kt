package com.beancounter.position.accumulation

import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.position.Constants.Companion.AUD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest(classes = [Accumulator::class])
internal class DividendTest {

    @Autowired
    private lateinit var accumulator: Accumulator

    @Test
    fun is_CashDividendAccumulated() {
        val asx = Market("ASX", AUD)
        val asset = getAsset(asx, "MO")
        val trn = Trn(TrnType.DIVI, asset)
        trn.tradeCashRate = BigDecimal("0.8988")
        trn.tradeAmount = BigDecimal("12.99")
        val positions = Positions()
        val position = positions[asset]
        accumulator.accumulate(trn, positions.portfolio, position)
        assertThat(position.getMoneyValues(Position.In.TRADE, asset.market.currency))
            .hasFieldOrPropertyWithValue("dividends", trn.tradeAmount)
    }
}
