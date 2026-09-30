package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.providers.eodhd.model.EodhdQuote
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Quote paths that don't need an HTTP stub: rate limiting happens in the proxy,
 * before any request is made.
 */
internal class EodhdQuoteTest {
    private val eodhdConfig =
        mock<EodhdConfig> {
            on { getPriceCode(any()) } doReturn "AAPL.US"
            on { apiKey } doReturn "test-key"
        }
    private val eodhdProxy = mock<EodhdProxy>()
    private val service = EodhdPriceService(eodhdConfig, eodhdProxy, mock(), DateUtils())

    @Test
    fun `quote is null when the EODHD rate limiter refuses the call`() {
        whenever(eodhdProxy.getRealTime(any(), any())) doThrow
            RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("eodhd"))

        assertThat(service.getQuote(AAPL)).isNull()
    }

    @Test
    fun `quote clamps a volume beyond Int range instead of wrapping`() {
        whenever(eodhdProxy.getRealTime(any(), any())) doReturn
            EodhdQuote(
                code = "AAPL.US",
                timestamp = 1727640000L,
                close = "227.79",
                previousClose = "227.52",
                volume = 3_000_000_000L
            )

        assertThat(service.getQuote(AAPL)!!.volume).isEqualTo(Int.MAX_VALUE)
    }
}