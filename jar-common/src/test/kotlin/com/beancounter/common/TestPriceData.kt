package com.beancounter.common

import com.beancounter.common.Constants.Companion.NYSE
import com.beancounter.common.Constants.Companion.P_CHANGE
import com.beancounter.common.Constants.Companion.P_CHANGE_PERCENT
import com.beancounter.common.Constants.Companion.P_CLOSE
import com.beancounter.common.Constants.Companion.P_OPEN
import com.beancounter.common.Constants.Companion.P_PREVIOUS_CLOSE
import com.beancounter.common.Constants.Companion.one
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
    private val asset =
        Asset(
            code = "ABC",
            market = NYSE,
        )

    @Test
    fun is_PriceDataWithRates() {
        val marketData = MarketData(asset)
        marketData.changePercent = BigDecimal("1.01")
        marketData.previousClose = BigDecimal("1.00")
        marketData.change = BigDecimal("1.00")
        marketData.open = BigDecimal("2.00")
        marketData.close = BigDecimal("2.00")

        val noFx =
            PriceData.of(
                marketData,
                BigDecimal("1.0"),
            )
        assertThat(noFx)
            .hasFieldOrPropertyWithValue(
                P_OPEN,
                marketData.open,
            ).hasFieldOrPropertyWithValue(
                P_PREVIOUS_CLOSE,
                marketData.previousClose,
            ).hasFieldOrPropertyWithValue(
                P_CLOSE,
                marketData.close,
            ).hasFieldOrPropertyWithValue(
                P_CHANGE,
                marketData.change,
            ).hasFieldOrPropertyWithValue(
                P_CHANGE_PERCENT,
                marketData.changePercent,
            )
        val withFx =
            PriceData.of(
                marketData,
                two,
            )
        assertThat(withFx)
            .hasFieldOrPropertyWithValue(
                P_OPEN,
                BigDecimal("4.00"),
            ).hasFieldOrPropertyWithValue(
                P_CLOSE,
                BigDecimal("4.00"),
            ).hasFieldOrPropertyWithValue(
                P_PREVIOUS_CLOSE,
                BigDecimal("2.00"),
            ).hasFieldOrPropertyWithValue(
                P_CHANGE,
                BigDecimal("2.00"),
            ).hasFieldOrPropertyWithValue(
                P_CHANGE_PERCENT,
                BigDecimal("0.5000"),
            )
    }

    @Test
    fun is_ChangeWithRatesComputing() {
        val marketData = MarketData(asset)

        marketData.previousClose = BigDecimal("40.92")
        marketData.close = BigDecimal("41.35")
        marketData.change = BigDecimal("0.43")
        marketData.changePercent = BigDecimal("0.0104")

        val noFx =
            PriceData.of(
                marketData,
                BigDecimal("1.0"),
            )
        assertThat(noFx)
            .hasFieldOrPropertyWithValue(
                P_PREVIOUS_CLOSE,
                marketData.previousClose,
            ).hasFieldOrPropertyWithValue(
                P_CLOSE,
                marketData.close,
            ).hasFieldOrPropertyWithValue(
                P_CHANGE,
                marketData.change,
            ).hasFieldOrPropertyWithValue(
                P_CHANGE_PERCENT,
                marketData.changePercent,
            )
        val withFx =
            PriceData.of(
                marketData,
                two,
            )
        assertThat(withFx)
            .hasFieldOrPropertyWithValue(
                P_PREVIOUS_CLOSE,
                BigDecimal("81.84"),
            ).hasFieldOrPropertyWithValue(
                P_CLOSE,
                BigDecimal("82.70"),
            ).hasFieldOrPropertyWithValue(
                P_CHANGE,
                BigDecimal("0.86"),
            ).hasFieldOrPropertyWithValue(
                P_CHANGE_PERCENT,
                BigDecimal("0.0104"),
            )
    }

    @Test
    fun is_PriceDataNullOk() {
        val marketData = MarketData(asset)
        val change = "1.01"
        marketData.changePercent = BigDecimal(change)
        marketData.previousClose = one
        marketData.change = one
        marketData.open = two
        marketData.close = two
        var withFx =
            PriceData.of(
                marketData,
                BigDecimal("1.1"),
            )
        assertThat(withFx).isNotNull

        val noFx = PriceData.of(marketData)
        assertThat(noFx).isNotNull

        val mdWithFx = MarketData(asset)
        mdWithFx.changePercent = BigDecimal(change)
        mdWithFx.previousClose = one
        mdWithFx.change = one
        mdWithFx.open = two

        withFx =
            PriceData.of(
                mdWithFx,
                BigDecimal("1.1"),
            )
        assertThat(withFx).isNotNull
    }
}
