package com.beancounter.marketdata.providers.sec

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.HttpStatusCodeException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

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
    fun `should keep the first row when two rows normalise to the same ticker`() {
        val proxy: SecProxy = mock()
        whenever(proxy.getCompanyTickers()).thenReturn(
            """{"0":{"cik_str":320193,"ticker":"AAPL","title":"Apple"},"1":{"cik_str":999,"ticker":" aapl ","title":"Dup"}}"""
        )

        val directory = SecTickerDirectory(proxy).tickerToCik()

        assertThat(directory).hasSize(1).containsEntry("AAPL", "0000320193")
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

    @Test
    fun `should not re-request the SEC index while SEC is throttling us`() {
        val proxy: SecProxy = mock()
        whenever(proxy.getCompanyTickers()).thenThrow(forbidden())
        val throttled = SecTickerResolver(SecTickerDirectory(proxy))

        repeat(3) {
            assertThatThrownBy { throttled.resolve(asset("AAPL")) }.isInstanceOf(SecThrottledException::class.java)
        }

        verify(proxy, times(1)).getCompanyTickers()
    }

    @Test
    fun `should request the SEC index again once the throttle cooldown has passed`() {
        val proxy: SecProxy = mock()
        whenever(proxy.getCompanyTickers())
            .thenThrow(forbidden())
            .thenReturn(ClassPathResource("mock/sec/company_tickers.json").file.readText())
        val clock = MutableClock(Instant.parse("2026-10-19T00:00:00Z"))
        val throttled = SecTickerResolver(SecTickerDirectory(proxy), clock)

        assertThatThrownBy { throttled.resolve(asset("AAPL")) }.isInstanceOf(SecThrottledException::class.java)
        clock.advance(SecTickerResolver.THROTTLE_COOLDOWN.plusSeconds(1))

        assertThat(throttled.resolve(asset("AAPL"))).isEqualTo("0000320193")
    }

    @Test
    fun `should pass any other index failure through and retry it on the next lookup`() {
        val proxy: SecProxy = mock()
        whenever(proxy.getCompanyTickers()).thenThrow(
            HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders(), ByteArray(0), null)
        )
        val failing = SecTickerResolver(SecTickerDirectory(proxy))

        repeat(2) {
            assertThatThrownBy { failing.resolve(asset("AAPL")) }
                .isInstanceOf(HttpStatusCodeException::class.java)
                .isNotInstanceOf(SecThrottledException::class.java)
        }

        verify(proxy, times(2)).getCompanyTickers()
    }

    private fun forbidden() =
        HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders(), ByteArray(0), null)

    private class MutableClock(
        private var now: Instant
    ) : Clock() {
        fun advance(by: Duration) {
            now = now.plus(by)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this
    }
}