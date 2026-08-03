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
            market = NYSE
        )

    @Test
    fun is_PriceDataWithRates() {
        val marketData =
            MarketData(
                asset,
                changePercent = BigDecimal(".01"),
                previousClose = BigDecimal("1.00"),
                change = BigDecimal("1.00"),
                open = BigDecimal("2.00"),
                close = BigDecimal("2.00")
            )

        val noFx =
            PriceData.of(
                marketData,
                rate = BigDecimal("1.0")
            )
        assertThat(noFx)
            .hasFieldOrPropertyWithValue(
                P_OPEN,
                marketData.open
            ).hasFieldOrPropertyWithValue(
                P_PREVIOUS_CLOSE,
                marketData.previousClose
            ).hasFieldOrPropertyWithValue(
                P_CLOSE,
                marketData.close
            ).hasFieldOrPropertyWithValue(
                P_CHANGE,
                marketData.change
            ).hasFieldOrPropertyWithValue(
                P_CHANGE_PERCENT,
                marketData.changePercent
            )
        val withFx =
            PriceData.of(
                marketData,
                two
            )
        // Converted prices are compared numerically - a conversion keeps whatever scale
        // the multiplication yields, so 4.000 and 4.00 are the same price.
        assertThat(withFx.open).isEqualByComparingTo(BigDecimal("4.00"))
        assertThat(withFx.close).isEqualByComparingTo(BigDecimal("4.00"))
        assertThat(withFx.previousClose).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(withFx.change).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(withFx.changePercent).isEqualByComparingTo(BigDecimal("0.01"))
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
                BigDecimal("1.0")
            )
        assertThat(noFx)
            .hasFieldOrPropertyWithValue(
                P_PREVIOUS_CLOSE,
                marketData.previousClose
            ).hasFieldOrPropertyWithValue(
                P_CLOSE,
                marketData.close
            ).hasFieldOrPropertyWithValue(
                P_CHANGE,
                marketData.change
            ).hasFieldOrPropertyWithValue(
                P_CHANGE_PERCENT,
                marketData.changePercent
            )
        val withFx =
            PriceData.of(
                marketData,
                two
            )
        assertThat(withFx.previousClose).isEqualByComparingTo(BigDecimal("81.84"))
        assertThat(withFx.close).isEqualByComparingTo(BigDecimal("82.70"))
        assertThat(withFx.change).isEqualByComparingTo(BigDecimal("0.86"))
        assertThat(withFx.changePercent).isEqualByComparingTo(BigDecimal("0.0104"))
    }

    @Test
    fun is_UnitPricePrecisionRetained() {
        // Unitised funds price beyond cents. Rounding a price to the money scale hides
        // sub-cent edits and overstates market value, which is computed from this close.
        val marketData =
            MarketData(
                asset,
                previousClose = BigDecimal("1.5900"),
                open = BigDecimal("1.5968"),
                close = BigDecimal("1.5968")
            )

        assertThat(
            PriceData.of(
                marketData,
                BigDecimal.ONE
            )
        ).hasFieldOrPropertyWithValue(
            P_CLOSE,
            BigDecimal("1.5968")
        ).hasFieldOrPropertyWithValue(
            P_OPEN,
            BigDecimal("1.5968")
        )

        assertThat(
            PriceData.of(
                marketData,
                BigDecimal("0.5887")
            )
        ).hasFieldOrPropertyWithValue(
            P_CLOSE,
            BigDecimal("0.94003616")
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
                BigDecimal("1.1")
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
                BigDecimal("1.1")
            )
        assertThat(withFx).isNotNull
    }
}