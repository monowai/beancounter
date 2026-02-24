package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.ProviderArguments
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Verifies that AlphaPriceService returns partial results when
 * some batch requests fail (e.g., rate limiter exceeded).
 * Previously, a single failure would discard all successfully retrieved prices.
 */
class AlphaPartialResultsTest {
    private lateinit var alphaConfig: AlphaConfig
    private lateinit var alphaProxy: AlphaProxy
    private lateinit var alphaPriceAdapter: AlphaPriceAdapter
    private lateinit var service: AlphaPriceService

    private val market = Market(code = "NASDAQ", currencyId = "USD")
    private val asset1 = Asset(id = "a1", code = "AAPL", market = market)
    private val asset2 = Asset(id = "a2", code = "MSFT", market = market)
    private val asset3 = Asset(id = "a3", code = "GOOG", market = market)

    @BeforeEach
    fun setUp() {
        alphaConfig = mock()
        alphaProxy = mock()
        alphaPriceAdapter = mock()

        whenever(alphaConfig.getBatchSize()).thenReturn(1)
        whenever(alphaConfig.getPriceCode(any())).thenAnswer {
            (it.arguments[0] as Asset).code
        }
        whenever(alphaConfig.dateUtils).thenReturn(DateUtils())

        service = AlphaPriceService(alphaConfig)
        service.setAlphaHelpers(alphaProxy, alphaPriceAdapter)

        // Set apiKey via reflection (normally @Value injected)
        AlphaPriceService::class.java.getDeclaredField("apiKey").apply {
            isAccessible = true
            set(service, "test-key")
        }
    }

    private fun priceRequest(vararg assets: Asset): PriceRequest =
        PriceRequest(
            date = "2026-02-09",
            assets = assets.map { PriceAsset(it) },
            currentMode = true
        )

    @Test
    fun `returns partial results when rate limiter blocks some requests`() {
        val validJson = """{"valid": "json"}"""

        // First two assets succeed, third throws rate limit
        whenever(alphaProxy.getCurrent(eq("AAPL"), any())).thenReturn(validJson)
        whenever(alphaProxy.getCurrent(eq("MSFT"), any())).thenReturn(validJson)
        whenever(alphaProxy.getCurrent(eq("GOOG"), any())).thenThrow(
            RequestNotPermitted.createRequestNotPermitted(
                RateLimiter.ofDefaults("alphaVantage")
            )
        )

        // Adapter returns MarketData for successful batches
        val md1 = MarketData(asset = asset1)
        val md2 = MarketData(asset = asset2)
        whenever(alphaPriceAdapter.get(any<ProviderArguments>(), eq(0), eq(validJson), eq(true)))
            .thenReturn(listOf(md1))
        whenever(alphaPriceAdapter.get(any<ProviderArguments>(), eq(1), eq(validJson), eq(true)))
            .thenReturn(listOf(md2))

        val results = service.getMarketData(priceRequest(asset1, asset2, asset3))

        // Should return 2 successful results, not 0
        assertThat(results).hasSize(2)
        assertThat(results.map { it.asset.code }).containsExactlyInAnyOrder("AAPL", "MSFT")
    }

    @Test
    fun `returns empty when all requests fail`() {
        whenever(alphaProxy.getCurrent(any(), any())).thenThrow(
            RequestNotPermitted.createRequestNotPermitted(
                RateLimiter.ofDefaults("alphaVantage")
            )
        )

        val results = service.getMarketData(priceRequest(asset1))

        assertThat(results).isEmpty()
    }

    @Test
    fun `returns all results when no failures`() {
        val validJson = """{"valid": "json"}"""

        whenever(alphaProxy.getCurrent(any(), any())).thenReturn(validJson)

        val md1 = MarketData(asset = asset1)
        val md2 = MarketData(asset = asset2)
        whenever(alphaPriceAdapter.get(any<ProviderArguments>(), eq(0), eq(validJson), eq(true)))
            .thenReturn(listOf(md1))
        whenever(alphaPriceAdapter.get(any<ProviderArguments>(), eq(1), eq(validJson), eq(true)))
            .thenReturn(listOf(md2))

        val results = service.getMarketData(priceRequest(asset1, asset2))

        assertThat(results).hasSize(2)
    }
}