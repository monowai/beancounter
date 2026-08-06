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

    @Test
    fun `getPriceCode honours the market's alpha alias`() {
        // BC's LSE market (USD-settled London lines, e.g. VUAA) is not a symbol suffix
        // AlphaVantage recognises - SYMBOL_SEARCH returns VUAA.LON. Without the alias the
        // code composed VUAA.LSE and every fundamentals call came back empty.
        val asset =
            Asset(
                code = "VUAA",
                market =
                    Market(
                        code = "LSE",
                        aliases = mapOf("alpha" to "LON")
                    )
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("VUAA.LON")
    }

    @Test
    fun `getPriceCode falls back to the market code when no alpha alias is configured`() {
        val asset =
            Asset(
                code = "VUAA",
                market = Market(code = "LSE")
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("VUAA.LSE")
    }

    @Test
    fun `getPriceCode ignores a blank alpha alias`() {
        val asset =
            Asset(
                code = "BHP",
                market =
                    Market(
                        code = "ASX",
                        aliases = mapOf("alpha" to "")
                    )
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("BHP.AX")
    }

    @Test
    fun `getPriceCode keeps US-aggregator markets suffix-free even with an alpha alias`() {
        val asset =
            Asset(
                code = "AAPL",
                market =
                    Market(
                        code = "NASDAQ",
                        aliases = mapOf("alpha" to "NASDAQ")
                    )
            )
        assertThat(alphaConfig.getPriceCode(asset)).isEqualTo("AAPL")
    }
}