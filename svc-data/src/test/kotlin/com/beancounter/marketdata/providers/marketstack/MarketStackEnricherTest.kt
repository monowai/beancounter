package com.beancounter.marketdata.providers.marketstack

import com.beancounter.marketdata.assets.AccountingTypeService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.marketstack.model.MarketStackExchangeData
import com.beancounter.marketdata.providers.marketstack.model.MarketStackTicker
import com.beancounter.marketdata.providers.marketstack.model.MarketStackTickerResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Unit tests for MarketStackEnricher.pickTicker matching logic.
 */
class MarketStackEnricherTest {
    private val enricher =
        MarketStackEnricher(
            mock(MarketStackGateway::class.java),
            mock(MarketStackConfig::class.java),
            mock(DefaultEnricher::class.java),
            mock(AccountingTypeService::class.java),
            mock(CurrencyService::class.java)
        )

    private val keppelReit = MarketStackTicker(name = "Keppel Real Estate Investment Trust", symbol = "K71U.SI")
    private val keppelCorp = MarketStackTicker(name = "Keppel Ltd", symbol = "BN4.SI")

    @Test
    fun `ambiguous fuzzy name matches return null to prevent wrong ticker assignment`() {
        // "KEP" hits both K71U (Keppel REIT) and BN4 (Keppel Corp) via name "Keppel" ⊇ "Kep".
        // firstOrNull previously picked K71U (came first), storing wrong priceSymbol K71U.SI.
        // Fix: multiple fuzzy matches → null → caller uses market-alias fallback (KEP.SI).
        val result = enricher.pickTicker(listOf(keppelReit, keppelCorp), "KEP")
        assertThat(result).isNull()
    }

    @Test
    fun `exact base-symbol match wins regardless of result order`() {
        // Even though K71U appears first, "BN4" exactly matches BN4.SI base symbol.
        val result = enricher.pickTicker(listOf(keppelReit, keppelCorp), "BN4")
        assertThat(result?.symbol).isEqualTo("BN4.SI")
    }

    @Test
    fun `single unambiguous name match is accepted`() {
        // Only one result matches "OCBC" by name — accept it.
        val ocbc = MarketStackTicker(name = "Oversea-Chinese Banking Corporation (OCBC)", symbol = "O39.SI")
        val result = enricher.pickTicker(listOf(ocbc), "OCBC")
        assertThat(result?.symbol).isEqualTo("O39.SI")
    }

    @Test
    fun `no matches returns null`() {
        val result = enricher.pickTicker(listOf(keppelReit, keppelCorp), "AAPL")
        assertThat(result).isNull()
    }
}