package com.beancounter.marketdata.providers.figi

import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.figi.FigiAdapter
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.assets.figi.FigiProxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class FigiProxyAliasTest {
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
}