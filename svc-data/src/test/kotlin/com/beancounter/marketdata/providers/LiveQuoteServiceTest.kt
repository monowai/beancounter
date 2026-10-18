package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Live quotes are fetched per provider in one call and reused for a minute, which is how often
 * the provider refreshes them.
 */
class LiveQuoteServiceTest {
    private var now = Instant.parse("2026-10-16T15:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant() = now
        }

    private fun quote(asset: Asset) =
        MarketData(asset = asset, priceDate = LocalDate.parse("2026-10-16"), close = BigDecimal("101"))

    private val provider =
        mock<MarketDataPriceProvider> {
            on { getQuotes(any()) } doAnswer { invocation ->
                invocation
                    .getArgument<Collection<Asset>>(0)
                    .filter { it.code == AAPL.code }
                    .associate { it.id to quote(it) }
            }
        }
    private val mdFactory =
        mock<MdFactory> {
            on { getMarketDataProvider(any<com.beancounter.common.model.Market>()) } doReturn
                provider
        }
    private val service = LiveQuoteService(mdFactory, clock)

    @Test
    fun `should return each provider's live quote keyed by asset`() {
        val quotes = service.quotes(listOf(AAPL, MSFT))

        assertThat(quotes.keys).containsExactly(AAPL.id)
        assertThat(quotes.getValue(AAPL.id).close).isEqualByComparingTo("101")
    }

    @Test
    fun `should reuse quotes, and misses, within a minute`() {
        service.quotes(listOf(AAPL, MSFT))
        now = now.plusSeconds(30)

        val again = service.quotes(listOf(AAPL, MSFT))

        assertThat(again.keys).containsExactly(AAPL.id)
        verify(provider, times(1)).getQuotes(any())
    }

    @Test
    fun `should fetch again once a minute has passed`() {
        service.quotes(listOf(AAPL))
        now = now.plus(Duration.ofSeconds(61))

        service.quotes(listOf(AAPL))

        verify(provider, times(2)).getQuotes(any())
    }

    @Test
    fun `should only ask the provider for assets it has not quoted recently`() {
        service.quotes(listOf(AAPL))

        service.quotes(listOf(AAPL, MSFT))

        verify(provider).getQuotes(argThat { map { it.id } == listOf(MSFT.id) })
    }

    @Test
    fun `should ask nothing for no assets`() {
        assertThat(service.quotes(emptyList())).isEmpty()
        verify(provider, times(0)).getQuotes(any())
    }
}