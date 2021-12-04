package com.beancounter.common

import com.beancounter.common.Constants.Companion.NYSE
import com.beancounter.common.Constants.Companion.changePercentProp
import com.beancounter.common.Constants.Companion.changeProp
import com.beancounter.common.Constants.Companion.closeProp
import com.beancounter.common.Constants.Companion.one
import com.beancounter.common.Constants.Companion.openProp
import com.beancounter.common.Constants.Companion.previousCloseProp
import com.beancounter.common.Constants.Companion.two
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.PriceData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Pojo Price tests
 */
class TestPriceData {
    val asset = Asset(code = "ABC", market = NYSE)

    @Test
    fun is_PriceDataWithRates() {
        val marketData = MarketData(asset)
        marketData.changePercent = BigDecimal("1.01")
        marketData.previousClose = BigDecimal("1.00")
        marketData.change = BigDecimal("1.00")
        marketData.open = BigDecimal("2.00")
        marketData.close = BigDecimal("2.00")

        val noFx = PriceData.of(marketData, BigDecimal("1.0"))
        assertThat(noFx)
            .hasFieldOrPropertyWithValue(openProp, marketData.open)
            .hasFieldOrPropertyWithValue(previousCloseProp, marketData.previousClose)
            .hasFieldOrPropertyWithValue(closeProp, marketData.close)
            .hasFieldOrPropertyWithValue(changeProp, marketData.change)
            .hasFieldOrPropertyWithValue(changePercentProp, marketData.changePercent)
        val withFx = PriceData.of(marketData, two)
        assertThat(withFx)
            .hasFieldOrPropertyWithValue(openProp, BigDecimal("4.00"))
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal("4.00"))
            .hasFieldOrPropertyWithValue(previousCloseProp, BigDecimal("2.00"))
            .hasFieldOrPropertyWithValue(changeProp, BigDecimal("2.00"))
            .hasFieldOrPropertyWithValue(changePercentProp, BigDecimal("0.5000"))
    }

    @Test
    fun is_ChangeWithRatesComputing() {
        val marketData = MarketData(asset)

        marketData.previousClose = BigDecimal("40.92")
        marketData.close = BigDecimal("41.35")
        marketData.change = BigDecimal("0.43")
        marketData.changePercent = BigDecimal("0.0104")

        val noFx = PriceData.of(marketData, BigDecimal("1.0"))
        assertThat(noFx)
            .hasFieldOrPropertyWithValue(previousCloseProp, marketData.previousClose)
            .hasFieldOrPropertyWithValue(closeProp, marketData.close)
            .hasFieldOrPropertyWithValue(changeProp, marketData.change)
            .hasFieldOrPropertyWithValue(changePercentProp, marketData.changePercent)
        val withFx = PriceData.of(marketData, two)
        assertThat(withFx)
            .hasFieldOrPropertyWithValue(previousCloseProp, BigDecimal("81.84"))
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal("82.70"))
            .hasFieldOrPropertyWithValue(changeProp, BigDecimal("0.86"))
            .hasFieldOrPropertyWithValue(changePercentProp, BigDecimal("0.0104"))
    }

    @Test
    fun is_PriceDataNullOk() {
        val marketData = MarketData(asset)
        val change = "1.01"
        marketData.changePercent = BigDecimal(change)
        marketData.previousClose = null
        marketData.change = one
        marketData.open = two
        marketData.close = two
        var withFx = PriceData.of(marketData, BigDecimal("1.1"))
        assertThat(withFx).isNotNull

        val noFx = PriceData.of(marketData)
        assertThat(noFx).isNotNull

        val mdWithFx = MarketData(asset)
        mdWithFx.changePercent = BigDecimal(change)
        mdWithFx.previousClose = one
        mdWithFx.change = one
        mdWithFx.open = two

        withFx = PriceData.of(mdWithFx, BigDecimal("1.1"))
        assertThat(withFx).isNotNull
    }
}
