package com.beancounter.marketdata.providers.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.figi.FigiAdapter
import com.beancounter.marketdata.assets.figi.FigiAsset
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.assets.figi.FigiResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class FigiProxyAliasTest {
    companion object {
        private const val API_KEY = "demoxx"
        private const val MOTU_NAME = "VANECK MSTAR WIDE MOAT ETF"
    }

    @Test
    fun `find skips FIGI and returns null when the market has no FIGI alias`() {
        // DATA-5K: enriching an asset on a market without a FIGI exchange alias
        // (e.g. the USD-on-LSE market, which only carries an eodhd alias) used to
        // NPE on `market.aliases["FIGI"]!!`. It must instead return null so the
        // enricher chain falls back to the default enricher.
        val gateway = Mockito.mock(FigiGateway::class.java)
        val proxy =
            FigiProxy(
                Mockito.mock(FigiConfig::class.java),
                gateway,
                Mockito.mock(FigiAdapter::class.java)
            )
        val lseUsd = Market(code = "LSE", aliases = mapOf("eodhd" to "LSE"))

        val result = proxy.find(lseUsd, "VUAA")

        assertThat(result).isNull()
        // No FIGI HTTP call should be attempted for an unmappable market.
        Mockito.verifyNoInteractions(gateway)
    }

    @Test
    fun `find advances past v3 warning no-match and resolves an LSE ETF as Mutual Fund`() {
        // OpenFIGI v3 signals a no-match with `warning` (data == null && error == null),
        // not `error`. An LSE ETF (e.g. MOTU) is classified securityType2 = "Mutual Fund".
        // The retry chain must keep advancing on a warning no-match so it reaches the
        // Mutual Fund attempt rather than stopping after Common Stock.
        val config = Mockito.mock(FigiConfig::class.java)
        whenever(config.apiKey).thenReturn(API_KEY)
        val gateway = Mockito.mock(FigiGateway::class.java)
        val adapter = Mockito.mock(FigiAdapter::class.java)
        val proxy = FigiProxy(config, gateway, adapter)
        val lse = Market(code = "LSE", aliases = mapOf(FigiProxy.FIGI to "LN"))

        // OpenFIGI mutates the shared FigiSearch in place per securityType, so match on
        // call order instead of the (live, mutated) argument: Common Stock + Depositary
        // Receipt return a `warning` no-match; the Mutual Fund attempt returns data.
        val warningNoMatch =
            listOf(FigiResponse(data = null, error = null).also { it.warning = "No identifier found." })
        val mutualFundMatch =
            listOf(
                FigiResponse(
                    data = listOf(FigiAsset(MOTU_NAME, "MOTU", "Mutual Fund")),
                    error = null
                )
            )
        whenever(gateway.search(any(), eq(API_KEY)))
            .thenReturn(warningNoMatch) // Common Stock
            .thenReturn(warningNoMatch) // Depositary Receipt
            .thenReturn(mutualFundMatch) // Mutual Fund -> match

        val enriched =
            Asset(code = "MOTU", id = "MOTU", name = MOTU_NAME, market = lse)
        whenever(
            adapter.transform(any<Market>(), any<String>(), any<FigiAsset>(), any<String>())
        ).thenReturn(enriched)

        val result = proxy.find(lse, "MOTU")

        assertThat(result)
            .isNotNull
            .hasFieldOrPropertyWithValue("name", MOTU_NAME)
        // Stops at Mutual Fund — REIT is never attempted.
        Mockito.verify(gateway, Mockito.times(3)).search(any(), eq(API_KEY))
    }

    @Test
    fun `find returns null on a true v3 warning no-match so the enricher falls back`() {
        // Every securityType returns a `warning` no-match -> FIGI yields null and the
        // enricher chain falls back to DefaultEnricher. The asset must NOT be created
        // here with a null name.
        val config = Mockito.mock(FigiConfig::class.java)
        whenever(config.apiKey).thenReturn(API_KEY)
        val gateway = Mockito.mock(FigiGateway::class.java)
        val adapter = Mockito.mock(FigiAdapter::class.java)
        val proxy = FigiProxy(config, gateway, adapter)
        val lse = Market(code = "LSE", aliases = mapOf(FigiProxy.FIGI to "LN"))

        val warningNoMatch =
            listOf(FigiResponse(data = null, error = null).also { it.warning = "No identifier found." })
        whenever(gateway.search(any(), eq(API_KEY))).thenReturn(warningNoMatch)

        val result = proxy.find(lse, "NOPE")

        assertThat(result).isNull()
        Mockito.verifyNoInteractions(adapter)
    }
}