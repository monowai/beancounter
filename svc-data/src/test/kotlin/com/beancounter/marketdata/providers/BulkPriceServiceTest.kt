package com.beancounter.marketdata.providers

import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.assets.AssetFinder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class BulkPriceServiceTest {
    private lateinit var priceService: PriceService
    private lateinit var marketDataRepo: MarketDataRepo
    private val assetFinder = mock(AssetFinder::class.java)

    @BeforeEach
    fun setUp() {
        marketDataRepo = mock(MarketDataRepo::class.java)
        priceService = PriceService(marketDataRepo, CashUtils(), assetFinder)
    }

    @Test
    fun `getBulkMarketData returns empty map for empty inputs`() {
        val result = priceService.getBulkMarketData(emptyList(), emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `getBulkMarketData returns exact matches keyed by date`() {
        val date1 = LocalDate.of(2024, 1, 15)
        val date2 = LocalDate.of(2024, 2, 15)
        val assets = listOf(AAPL, MSFT)
        val dates = listOf(date1, date2)

        val md1 = MarketData(asset = AAPL, priceDate = date1, close = BigDecimal("150"))
        val md2 = MarketData(asset = MSFT, priceDate = date1, close = BigDecimal("400"))
        val md3 = MarketData(asset = AAPL, priceDate = date2, close = BigDecimal("155"))
        val md4 = MarketData(asset = MSFT, priceDate = date2, close = BigDecimal("410"))

        `when`(marketDataRepo.findByAssetInAndPriceDateIn(assets, dates))
            .thenReturn(listOf(md1, md2, md3, md4))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result).hasSize(2)
        assertThat(result["2024-01-15"]).hasSize(2)
        assertThat(result["2024-02-15"]).hasSize(2)
    }

    @Test
    fun `getBulkMarketData falls back to nearest price for missing dates`() {
        val weekend = LocalDate.of(2024, 1, 13) // Saturday
        val assets = listOf(AAPL)
        val dates = listOf(weekend)

        // No exact match for Saturday
        `when`(marketDataRepo.findByAssetInAndPriceDateIn(assets, dates))
            .thenReturn(emptyList())

        // But Friday's price exists
        val fridayMd = MarketData(asset = AAPL, priceDate = LocalDate.of(2024, 1, 12), close = BigDecimal("148"))
        `when`(marketDataRepo.findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(AAPL, weekend))
            .thenReturn(Optional.of(fridayMd))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result).hasSize(1)
        assertThat(result["2024-01-13"]).hasSize(1)
        assertThat(result["2024-01-13"]!![0].close).isEqualByComparingTo(BigDecimal("148"))
    }

    @Test
    fun `getBulkMarketData handles mix of exact and fallback prices`() {
        val date1 = LocalDate.of(2024, 1, 15)
        val date2 = LocalDate.of(2024, 1, 13) // weekend
        val assets = listOf(AAPL)
        val dates = listOf(date1, date2)

        val exactMd = MarketData(asset = AAPL, priceDate = date1, close = BigDecimal("150"))
        `when`(marketDataRepo.findByAssetInAndPriceDateIn(assets, dates))
            .thenReturn(listOf(exactMd))

        // Fallback for weekend date
        val fallbackMd = MarketData(asset = AAPL, priceDate = LocalDate.of(2024, 1, 12), close = BigDecimal("148"))
        `when`(marketDataRepo.findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(AAPL, date2))
            .thenReturn(Optional.of(fallbackMd))

        val result = priceService.getBulkMarketData(assets, dates)

        assertThat(result).hasSize(2)
        assertThat(result["2024-01-15"]).hasSize(1)
        assertThat(result["2024-01-13"]).hasSize(1)
    }

    @Test
    fun `getBulkMarketData returns empty list for date with no data at all`() {
        val date = LocalDate.of(2024, 1, 1)
        val assets = listOf(AAPL)

        `when`(marketDataRepo.findByAssetInAndPriceDateIn(assets, listOf(date)))
            .thenReturn(emptyList())
        `when`(marketDataRepo.findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(AAPL, date))
            .thenReturn(Optional.empty())

        val result = priceService.getBulkMarketData(assets, listOf(date))

        assertThat(result).hasSize(1)
        assertThat(result["2024-01-01"]).isEmpty()
    }
}