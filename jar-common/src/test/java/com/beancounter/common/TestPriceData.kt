package com.beancounter.common

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.PriceData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TestPriceData {
    @Test
    fun is_PriceDataWithRates() {
        val marketData = MarketData(Asset(""))
        marketData.changePercent = BigDecimal("1.01")
        marketData.previousClose = BigDecimal("1.00")
        marketData.change = BigDecimal("1.00")
        marketData.open = BigDecimal("2.00")
        marketData.close = BigDecimal("2.00")

        val noFx = PriceData.of(marketData, BigDecimal("1.0"))
        assertThat(noFx)
                .hasFieldOrPropertyWithValue("open", marketData.open)
                .hasFieldOrPropertyWithValue("previousClose", marketData.previousClose)
                .hasFieldOrPropertyWithValue("close", marketData.close)
                .hasFieldOrPropertyWithValue("change", marketData.change)
                .hasFieldOrPropertyWithValue("changePercent", marketData.changePercent)
        val withFx = PriceData.of(marketData, BigDecimal("2.0"))
        assertThat(withFx)
                .hasFieldOrPropertyWithValue("open", BigDecimal("4.00"))
                .hasFieldOrPropertyWithValue("close", BigDecimal("4.00"))
                .hasFieldOrPropertyWithValue("previousClose", BigDecimal("2.00"))
                .hasFieldOrPropertyWithValue("change", BigDecimal("2.00"))
                .hasFieldOrPropertyWithValue("changePercent", BigDecimal("0.5000"))
    }

    @Test
    fun is_ChangeWithRatesComputing() {
        val marketData = MarketData(Asset("X"))

        marketData.previousClose = BigDecimal("40.92")
        marketData.close = BigDecimal("41.35")
        marketData.change = BigDecimal("0.43")
        marketData.changePercent = BigDecimal("0.0104")

        val noFx = PriceData.of(marketData, BigDecimal("1.0"))
        assertThat(noFx)
                .hasFieldOrPropertyWithValue("previousClose", marketData.previousClose)
                .hasFieldOrPropertyWithValue("close", marketData.close)
                .hasFieldOrPropertyWithValue("change", marketData.change)
                .hasFieldOrPropertyWithValue("changePercent", marketData.changePercent)
        val withFx = PriceData.of(marketData, BigDecimal("2.0"))
        assertThat(withFx)
                .hasFieldOrPropertyWithValue("previousClose", BigDecimal("81.84"))
                .hasFieldOrPropertyWithValue("close", BigDecimal("82.70"))
                .hasFieldOrPropertyWithValue("change", BigDecimal("0.86"))
                .hasFieldOrPropertyWithValue("changePercent", BigDecimal("0.0104"))
    }

    @Test
    fun is_PriceDataNullOk() {
        val marketData = MarketData(Asset(""))
        marketData.changePercent = BigDecimal("1.01")
        marketData.previousClose = null
        marketData.change = BigDecimal("1.00")
        marketData.open = BigDecimal("2.00")
        marketData.close = BigDecimal("2.00")
        var withFx = PriceData.of(marketData, BigDecimal("1.1"))
        assertThat(withFx).isNotNull

        val noFx = PriceData.of(marketData)
        assertThat(noFx).isNotNull

        val mdWithFx = MarketData(Asset(""))
        mdWithFx.changePercent = BigDecimal("1.01")
        mdWithFx.previousClose = BigDecimal("1.00")
        mdWithFx.change = BigDecimal("1.00")
        mdWithFx.open = BigDecimal("2.00")

        withFx = PriceData.of(mdWithFx, BigDecimal("1.1"))
        assertThat(withFx).isNotNull
    }
}