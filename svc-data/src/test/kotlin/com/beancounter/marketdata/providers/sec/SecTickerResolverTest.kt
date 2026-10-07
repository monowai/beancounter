package com.beancounter.marketdata.providers.sec

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource

/**
 * `company_tickers.json` is keyed by row index, not ticker, and share classes use `-` where
 * Beancounter codes use `.`. The resolver has to bridge both and hand back the zero-padded CIK
 * that the `data.sec.gov` endpoints expect.
 */
class SecTickerResolverTest {
    private lateinit var secProxy: SecProxy
    private lateinit var resolver: SecTickerResolver

    private val nasdaq = Market("NASDAQ")

    private fun asset(code: String) =
        Asset(id = "$code-id", code = code, name = code, market = nasdaq, status = Status.Active)

    @BeforeEach
    fun setUp() {
        secProxy = mock()
        whenever(secProxy.getCompanyTickers())
            .thenReturn(ClassPathResource("mock/sec/company_tickers.json").file.readText())
        resolver = SecTickerResolver(SecTickerDirectory(secProxy))
    }

    @Test
    fun `should resolve a ticker to its ten-digit zero-padded CIK`() {
        assertThat(resolver.resolve(asset("AAPL"))).isEqualTo("0000320193")
    }

    @Test
    fun `should translate a dotted share class to the SEC hyphen form`() {
        assertThat(resolver.resolve(asset("BRK.B"))).isEqualTo("0001067983")
    }

    @Test
    fun `should match case-insensitively`() {
        assertThat(resolver.resolve(asset("msft"))).isEqualTo("0000789019")
    }

    @Test
    fun `should return null for a ticker the SEC does not list`() {
        assertThat(resolver.resolve(asset("ZZZZ"))).isNull()
    }

    @Test
    fun `should drop an index row whose CIK is blank instead of minting a zero CIK`() {
        val proxy: SecProxy = mock()
        whenever(proxy.getCompanyTickers()).thenReturn(
            """{"0":{"cik_str":"","ticker":"NOCIK","title":"No CIK"},"1":{"cik_str":320193,"ticker":"AAPL","title":"Apple"}}"""
        )

        val directory = SecTickerDirectory(proxy).tickerToCik()

        assertThat(directory).doesNotContainKey("NOCIK").containsEntry("AAPL", "0000320193")
        assertThat(directory.values).doesNotContain("0000000000")
    }
}