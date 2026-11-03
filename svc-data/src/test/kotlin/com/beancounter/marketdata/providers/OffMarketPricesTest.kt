package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Off-market prices: a user-entered close for a PRIVATE asset is written; anything else is read.
 */
class OffMarketPricesTest {
    private val marketDataRepo = mock(MarketDataRepo::class.java)
    private val cacheInvalidationProducer = mock(CacheInvalidationProducer::class.java)
    private val assetFinder = mock(AssetFinder::class.java)
    private val offMarketPrices = OffMarketPrices(marketDataRepo, assetFinder, cacheInvalidationProducer)
    private val asset = Asset(code = "1", market = NASDAQ)
    private val privateAsset =
        Asset(
            code = "HOUSE",
            market = Market(PrivateMarketDataProvider.ID)
        )

    @BeforeEach
    fun setUp() {
        `when`(assetFinder.find(asset.id)).thenReturn(asset)
        `when`(assetFinder.find(privateAsset.id)).thenReturn(privateAsset)
    }

    @Test
    fun `should invalidate the price cache when a new private market price is written`() {
        val priceDate = LocalDate.of(2026, 3, 31)
        `when`(marketDataRepo.findByAssetIdAndPriceDate(privateAsset.id, priceDate))
            .thenReturn(Optional.empty())
        `when`(marketDataRepo.save(any<MarketData>())).thenAnswer { it.arguments[0] }

        val result =
            offMarketPrices.record(
                privateAsset.id,
                priceDate,
                BigDecimal("250000.00")
            )

        assertThat(result).isPresent
        val assetCaptor = argumentCaptor<String>()
        val dateCaptor = argumentCaptor<LocalDate>()
        verify(cacheInvalidationProducer)
            .sendPriceHistoryEvent(assetCaptor.capture(), dateCaptor.capture())
        assertThat(assetCaptor.firstValue).isEqualTo(privateAsset.id)
        assertThat(dateCaptor.firstValue).isEqualTo(priceDate)
    }

    @Test
    fun `should invalidate the price cache when an existing private market price is revalued`() {
        val priceDate = LocalDate.of(2026, 3, 31)
        val existing =
            MarketData(
                asset = privateAsset,
                priceDate = priceDate,
                close = BigDecimal("200000.00"),
                source = "USER"
            )
        `when`(marketDataRepo.findByAssetIdAndPriceDate(privateAsset.id, priceDate))
            .thenReturn(Optional.of(existing))
        `when`(marketDataRepo.save(any<MarketData>())).thenAnswer { it.arguments[0] }

        val result =
            offMarketPrices.record(
                privateAsset.id,
                priceDate,
                BigDecimal("250000.00")
            )

        assertThat(result).isPresent
        assertThat(result.get().close).isEqualByComparingTo(BigDecimal("250000.00"))
        val assetCaptor = argumentCaptor<String>()
        val dateCaptor = argumentCaptor<LocalDate>()
        verify(cacheInvalidationProducer)
            .sendPriceHistoryEvent(assetCaptor.capture(), dateCaptor.capture())
        assertThat(assetCaptor.firstValue).isEqualTo(privateAsset.id)
        assertThat(dateCaptor.firstValue).isEqualTo(priceDate)
    }

    @Test
    fun `should not invalidate the price cache when a private market read writes nothing`() {
        val priceDate = LocalDate.of(2026, 3, 31)
        `when`(marketDataRepo.findByAssetIdAndPriceDate(privateAsset.id, priceDate))
            .thenReturn(Optional.empty())

        // No closePrice supplied — this is a plain read, not a revaluation.
        val result = offMarketPrices.record(privateAsset.id, priceDate, BigDecimal.ZERO)

        assertThat(result).isEmpty
        verify(marketDataRepo, never()).save(any<MarketData>())
        verify(cacheInvalidationProducer, never()).sendPriceHistoryEvent(any(), any())
    }

    @Test
    fun `should return the stored price and write nothing for a listed asset`() {
        val date = LocalDate.now()
        val marketData = MarketData(asset, date, BigDecimal.TEN)

        `when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)).thenReturn(Optional.of(marketData))

        val result = offMarketPrices.record(asset.id, date, BigDecimal.TEN)

        assertThat(result).contains(marketData)
        verify(marketDataRepo, never()).save(any<MarketData>())
    }

    @Test
    fun `should return empty for a listed asset with no stored price`() {
        val date = LocalDate.now()

        `when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)).thenReturn(Optional.empty())

        val result = offMarketPrices.record(asset.id, date, BigDecimal.TEN)

        assertThat(result).isEmpty
    }
}