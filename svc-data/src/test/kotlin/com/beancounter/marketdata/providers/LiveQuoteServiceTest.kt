package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Live quotes are fetched per provider in one call and reused for a minute, which is how often
 * the provider refreshes them. Only markets in session are asked; concurrent reads share a fetch.
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
            on { getMarketDataProvider(any<Market>()) } doReturn
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
    fun `should treat a failing provider as having no quotes`() {
        val failing = mock<MarketDataPriceProvider> { on { getQuotes(any()) } doThrow IllegalStateException("down") }
        val factory = mock<MdFactory> { on { getMarketDataProvider(any<Market>()) } doReturn failing }

        assertThat(LiveQuoteService(factory, clock).quotes(listOf(AAPL))).isEmpty()
    }

    @Test
    fun `should ask nothing for no assets`() {
        assertThat(service.quotes(emptyList())).isEmpty()
        verify(provider, times(0)).getQuotes(any())
    }

    @Test
    fun `should not ask the provider for a market that is not in session`() {
        // 2026-10-17 is a Saturday.
        now = Instant.parse("2026-10-17T15:00:00Z")

        assertThat(service.quotes(listOf(AAPL))).isEmpty()
        verify(provider, times(0)).getQuotes(any())
    }

    @Test
    fun `should share one provider call between concurrent reads`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow =
            mock<MarketDataPriceProvider> {
                on { getQuotes(any()) } doAnswer { invocation ->
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    invocation.getArgument<Collection<Asset>>(0).associate { it.id to quote(it) }
                }
            }
        val factory = mock<MdFactory> { on { getMarketDataProvider(any<Market>()) } doReturn slow }
        val shared = LiveQuoteService(factory, clock)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<Map<String, MarketData>> { shared.quotes(listOf(AAPL)) }
            started.await(5, TimeUnit.SECONDS)
            val second = pool.submit<Map<String, MarketData>> { shared.quotes(listOf(AAPL)) }
            Thread.sleep(100)
            release.countDown()

            assertThat(first.get(5, TimeUnit.SECONDS).keys).containsExactly(AAPL.id)
            assertThat(second.get(5, TimeUnit.SECONDS).keys).containsExactly(AAPL.id)
            verify(slow, times(1)).getQuotes(any())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `should show a live quote over an older stored close`() {
        val stored = mapOf(AAPL.id to close(AAPL, "2026-10-15", "99"), MSFT.id to close(MSFT, "2026-10-15", "50"))

        val prices = service.livePrices(listOf(AAPL, MSFT), stored)

        assertThat(prices.getValue(AAPL.id).close).isEqualByComparingTo("101")
        assertThat(prices.getValue(MSFT.id).close).isEqualByComparingTo("50")
    }

    @Test
    fun `should keep a stored close that is newer than the live quote`() {
        val stored = mapOf(AAPL.id to close(AAPL, "2026-10-17", "99"))

        assertThat(service.livePrices(listOf(AAPL), stored).getValue(AAPL.id).close).isEqualByComparingTo("99")
    }

    @Test
    fun `should quote an asset with no stored close`() {
        assertThat(service.livePrices(listOf(AAPL), emptyMap()).getValue(AAPL.id).close).isEqualByComparingTo("101")
    }

    private fun close(
        asset: Asset,
        date: String,
        price: String
    ) = MarketData(asset = asset, priceDate = LocalDate.parse(date), close = BigDecimal(price))
}