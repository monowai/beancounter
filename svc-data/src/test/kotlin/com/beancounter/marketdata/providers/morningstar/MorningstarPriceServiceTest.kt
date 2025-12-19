package com.beancounter.marketdata.providers.morningstar

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Tests for the Morningstar price provider.
 * Uses mocked API responses to test the parsing and data handling.
 */
class MorningstarPriceServiceTest {
    private lateinit var morningstarConfig: MorningstarConfig
    private lateinit var morningstarProxy: MorningstarProxy
    private lateinit var morningstarPriceService: MorningstarPriceService

    private val dateUtils = DateUtils()

    companion object {
        // Sample API response for AXA Framlington Health Fund (GB00B6WZJX05)
        const val SAMPLE_PRICE_RESPONSE = """
        {
            "TimeSeries": {
                "Security": [{
                    "HistoryDetail": [
                        {"EndDate": "2024-12-18", "Value": 3.4567}
                    ]
                }]
            }
        }
        """
    }

    @BeforeEach
    fun setup() {
        morningstarConfig = MorningstarConfig(dateUtils)
        morningstarProxy = mock()
        morningstarPriceService = MorningstarPriceService(morningstarConfig)
        morningstarPriceService.setMorningstarProxy(morningstarProxy)
    }

    @Test
    fun `getId returns MORNINGSTAR`() {
        assertThat(morningstarPriceService.getId()).isEqualTo("MORNINGSTAR")
    }

    @Test
    fun `isMarketSupported returns true for MUTF market`() {
        val mutfMarket = Market("MUTF", "GBP")
        assertThat(morningstarPriceService.isMarketSupported(mutfMarket)).isTrue()
    }

    @Test
    fun `isMarketSupported returns false for other markets`() {
        val nasdaqMarket = Market("NASDAQ", "USD")
        assertThat(morningstarPriceService.isMarketSupported(nasdaqMarket)).isFalse()
    }

    @Test
    fun `getMarketData returns price for valid ISIN`() {
        val market = Market("MUTF", "GBP")
        val asset =
            Asset(
                code = "GB00B6WZJX05",
                id = "test-asset-id",
                name = "AXA Framlington Health Fund Z Acc",
                market = market,
                priceSymbol = "F00000P791" // Morningstar ID
            )

        whenever(morningstarProxy.getPrice(any<String>(), any<String>(), any<java.time.LocalDate>())).thenReturn(
            SAMPLE_PRICE_RESPONSE
        )

        val priceRequest = PriceRequest.of(asset)
        val result = morningstarPriceService.getMarketData(priceRequest)

        assertThat(result).hasSize(1)
        val marketData = result.first()
        assertThat(marketData.asset).isEqualTo(asset)
        assertThat(marketData.close).isEqualTo(BigDecimal("3.4567"))
    }

    @Test
    fun `getMarketData uses ISIN when priceSymbol not set`() {
        val market = Market("MUTF", "GBP")
        val asset =
            Asset(
                code = "GB00B6WZJX05",
                id = "test-asset-id",
                name = "AXA Framlington Health Fund Z Acc",
                market = market,
                priceSymbol = null // No Morningstar ID, use ISIN (code)
            )

        whenever(morningstarProxy.getPrice(any<String>(), any<String>(), any<java.time.LocalDate>())).thenReturn(
            SAMPLE_PRICE_RESPONSE
        )

        val priceRequest = PriceRequest.of(asset)
        val result = morningstarPriceService.getMarketData(priceRequest)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `getMarketData handles empty response gracefully`() {
        val market = Market("MUTF", "GBP")
        val asset =
            Asset(
                code = "INVALID_ISIN",
                id = "test-asset-id",
                market = market
            )

        val emptyResponse = """{"TimeSeries": {"Security": []}}"""
        whenever(morningstarProxy.getPrice(any(), any(), any())).thenReturn(emptyResponse)

        val priceRequest = PriceRequest.of(asset)
        val result = morningstarPriceService.getMarketData(priceRequest)

        assertThat(result).isEmpty()
    }

    @Test
    fun `isApiSupported returns true`() {
        assertThat(morningstarPriceService.isApiSupported()).isTrue()
    }

    @Test
    fun `getPriceCode returns priceSymbol when set`() {
        val market = Market("MUTF", "GBP")
        val asset =
            Asset(
                code = "GB00B6WZJX05",
                market = market,
                priceSymbol = "F00000P791"
            )

        assertThat(morningstarConfig.getPriceCode(asset)).isEqualTo("F00000P791")
    }

    @Test
    fun `getPriceCode falls back to code when priceSymbol not set`() {
        val market = Market("MUTF", "GBP")
        val asset =
            Asset(
                code = "GB00B6WZJX05",
                market = market,
                priceSymbol = null
            )

        assertThat(morningstarConfig.getPriceCode(asset)).isEqualTo("GB00B6WZJX05")
    }
}