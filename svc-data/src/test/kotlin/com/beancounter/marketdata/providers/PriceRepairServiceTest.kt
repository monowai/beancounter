package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.assets.AssetFinder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Repair endpoint stamps the `split` column on existing `MarketData` rows so
 * `SplitAdjuster` can rebase historical OHLC via column-transition detection
 * on every read path. Without this, historical backfills from providers that
 * don't carry split coefficients per row (Alpha `TIME_SERIES_DAILY`) leave
 * the DB with `split = 1` across the board and charts span splits raw.
 */
class PriceRepairServiceTest {
    private lateinit var priceService: PriceService
    private lateinit var marketDataRepo: MarketDataRepo
    private val assetFinder = mock(AssetFinder::class.java)
    private val eventServiceFacade = mock(EventServiceFacade::class.java)

    @BeforeEach
    fun setUp() {
        marketDataRepo = mock(MarketDataRepo::class.java)
        priceService = PriceService(marketDataRepo, CashUtils(), assetFinder)
        priceService.setEventServiceFacade(eventServiceFacade)
        whenever(assetFinder.find(AAPL.id)).thenReturn(AAPL)
    }

    @Test
    fun `repairSplits stamps split column on the ex-date row from provider events`() {
        val exDate = LocalDate.of(2020, 8, 31)
        val splitEvent =
            MarketData(asset = AAPL, priceDate = exDate, close = BigDecimal.ZERO).apply {
                split = BigDecimal("4")
            }
        whenever(eventServiceFacade.getEvents(AAPL.id))
            .thenReturn(PriceResponse(listOf(splitEvent)))

        val existing =
            MarketData(asset = AAPL, priceDate = exDate, close = BigDecimal("125.00"))
        whenever(marketDataRepo.findByAssetIdAndPriceDate(AAPL.id, exDate))
            .thenReturn(Optional.of(existing))

        val response = priceService.repairSplits(AAPL.id)

        assertThat(response.stamped).isEqualTo(1)
        assertThat(response.alreadyStamped).isEqualTo(0)
        assertThat(response.missingRows).isEqualTo(0)
        val saved = ArgumentCaptor.forClass(MarketData::class.java)
        verify(marketDataRepo).save(saved.capture())
        assertThat(saved.value.split).isEqualByComparingTo(BigDecimal("4"))
    }

    @Test
    fun `repairSplits leaves rows already stamped untouched`() {
        val exDate = LocalDate.of(2020, 8, 31)
        val splitEvent =
            MarketData(asset = AAPL, priceDate = exDate, close = BigDecimal.ZERO).apply {
                split = BigDecimal("4")
            }
        whenever(eventServiceFacade.getEvents(AAPL.id))
            .thenReturn(PriceResponse(listOf(splitEvent)))

        val existing =
            MarketData(asset = AAPL, priceDate = exDate, close = BigDecimal("125.00")).apply {
                split = BigDecimal("4")
            }
        whenever(marketDataRepo.findByAssetIdAndPriceDate(AAPL.id, exDate))
            .thenReturn(Optional.of(existing))

        val response = priceService.repairSplits(AAPL.id)

        assertThat(response.stamped).isEqualTo(0)
        assertThat(response.alreadyStamped).isEqualTo(1)
        verify(marketDataRepo, org.mockito.Mockito.never()).save(any<MarketData>())
    }

    @Test
    fun `repairSplits reports missing rows when no MarketData exists on the ex-date`() {
        val exDate = LocalDate.of(2020, 8, 31)
        val splitEvent =
            MarketData(asset = AAPL, priceDate = exDate, close = BigDecimal.ZERO).apply {
                split = BigDecimal("4")
            }
        whenever(eventServiceFacade.getEvents(AAPL.id))
            .thenReturn(PriceResponse(listOf(splitEvent)))
        whenever(marketDataRepo.findByAssetIdAndPriceDate(AAPL.id, exDate))
            .thenReturn(Optional.empty())

        val response = priceService.repairSplits(AAPL.id)

        assertThat(response.stamped).isEqualTo(0)
        assertThat(response.missingRows).isEqualTo(1)
        verify(marketDataRepo, org.mockito.Mockito.never()).save(any<MarketData>())
    }
}