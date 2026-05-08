package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.Constants.Companion.INDEX_MARKET
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AlphaConfigTest {
    private val alphaConfig = AlphaConfig()

    @Test
    fun `getPriceCode returns priceSymbol verbatim when set`() {
        val asset =
            Asset(
                code = "^GSPC",
                market = INDEX_MARKET,
                priceSymbol = "^GSPC"
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("^GSPC")
    }

    @Test
    fun `getPriceCode passes through caret-prefixed code without market suffix`() {
        val asset =
            Asset(
                code = "^GSPC",
                market = INDEX_MARKET
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("^GSPC")
    }

    @Test
    fun `getPriceCode appends market suffix for non-null market`() {
        val asset =
            Asset(
                code = "BHP",
                market = Market("ASX")
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("BHP.AX")
    }

    @Test
    fun `getPriceCode returns code only for US-aggregator markets`() {
        val asset =
            Asset(
                code = "AAPL",
                market = Market("NASDAQ")
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("AAPL")
    }
}