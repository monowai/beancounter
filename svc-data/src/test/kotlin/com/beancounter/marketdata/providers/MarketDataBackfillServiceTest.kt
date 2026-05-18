package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.trn.TrnRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Tests anchor + skip behaviour. Provider lineage is not preserved — these tests assert
 * the service treats any existing price as coverage, regardless of source.
 *
 * Service uses `LocalDate.now()` internally (matches `defaultBackfillFrom()` pattern), so
 * tests build expected values relative to today rather than fixing a calendar date.
 */
class MarketDataBackfillServiceTest {
    private lateinit var providerUtils: ProviderUtils
    private lateinit var priceService: PriceService
    private lateinit var assetFinder: AssetFinder
    private lateinit var marketDataRepo: MarketDataRepo
    private lateinit var trnRepository: TrnRepository
    private lateinit var cacheInvalidationProducer: CacheInvalidationProducer
    private lateinit var provider: MarketDataPriceProvider
    private lateinit var service: MarketDataBackfillService

    private val today: LocalDate get() = LocalDate.now()
    private val asset = Asset(code = "AAPL", id = "aapl-id", market = NASDAQ)

    @BeforeEach
    fun setUp() {
        providerUtils = mock()
        priceService = mock()
        assetFinder = mock()
        marketDataRepo = mock()
        trnRepository = mock()
        cacheInvalidationProducer = mock()
        provider = mock()

        whenever(providerUtils.getInputs(eq(listOf(asset))))
            .thenReturn(listOf(PriceAsset(asset)))
        whenever(providerUtils.splitProviders(any()))
            .thenReturn(mutableMapOf(provider to mutableListOf(asset)))
        whenever(provider.backFill(any(), any())).thenReturn(PriceResponse(emptyList()))

        service =
            MarketDataBackfillService(
                providerUtils,
                priceService,
                assetFinder,
                marketDataRepo,
                trnRepository,
                cacheInvalidationProducer,
                maxBackfillYears = 10
            )
    }

    @Test
    fun `anchor widens fromDate when older holdings exist in another portfolio`() {
        // Caller window starts 3y back; cross-portfolio earliest holding is 7y back — both inside 10y floor.
        val requested = today.minusYears(3)
        val earliestHeld = today.minusYears(7)
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(earliestHeld)
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(requested)
        whenever(marketDataRepo.findLatestPriceDateByAssetId(asset.id)).thenReturn(today)

        service.backFill(asset, requested)

        val captor = argumentCaptor<LocalDate>()
        verify(provider).backFill(eq(asset), captor.capture())
        assertThat(captor.firstValue).isEqualTo(earliestHeld)
    }

    @Test
    fun `anchor floors at maxBackfillYears so providers are not asked for 30 years`() {
        // Holdings reach back 30y; floor pulls request to today - 10y.
        val ancient = today.minusYears(30)
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(ancient)
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(null)

        service.backFill(asset, today.minusYears(2))

        val captor = argumentCaptor<LocalDate>()
        verify(provider).backFill(eq(asset), captor.capture())
        assertThat(captor.firstValue).isEqualTo(today.minusYears(10))
    }

    @Test
    fun `anchor keeps caller fromDate when no holdings recorded`() {
        val requested = today.minusYears(2)
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(null)
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(null)

        service.backFill(asset, requested)

        verify(provider).backFill(eq(asset), eq(requested))
    }

    @Test
    fun `skip provider call when DB already covers requested range`() {
        // DB has prices from 10y back to yesterday — full coverage of any reasonable request.
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(today.minusYears(10))
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(today.minusYears(10))
        whenever(marketDataRepo.findLatestPriceDateByAssetId(asset.id)).thenReturn(today.minusDays(1))

        service.backFill(asset, today.minusYears(5))

        verify(provider, never()).backFill(any(), any())
        verify(priceService, never()).handle(any())
    }

    @Test
    fun `call provider when DB coverage starts later than anchored fromDate`() {
        // Anchored to 8y back (cross-portfolio, inside 10y floor); DB only has 3y onwards → gap.
        val earliestHeld = today.minusYears(8)
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(earliestHeld)
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(today.minusYears(3))
        whenever(marketDataRepo.findLatestPriceDateByAssetId(asset.id)).thenReturn(today)

        service.backFill(asset, today.minusYears(3))

        verify(provider).backFill(eq(asset), eq(earliestHeld))
    }

    @Test
    fun `call provider when DB has no rows yet`() {
        val requested = today.minusYears(2)
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(null)
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(null)
        whenever(marketDataRepo.findLatestPriceDateByAssetId(asset.id)).thenReturn(null)

        service.backFill(asset, requested)

        verify(provider).backFill(eq(asset), eq(requested))
    }

    @Test
    fun `never fires PRICE_HISTORY invalidation from backFill path`() {
        // Backfill only inserts dates that aren't already present (PriceService.handle dedups
        // on (assetId, priceDate)). It can't modify a price inside the existing svc-position
        // snapshot range, so the event would always be a false invalidation. The OOM incident
        // (POSITION-3T..3V, 2026-05-18) was caused by sending the event with the widened
        // fromDate, which wiped years of snapshots. This test guards against regression by
        // asserting NO event fires regardless of dbMin/dbMax shape.
        whenever(trnRepository.findEarliestTradeDateByAssetId(asset.id)).thenReturn(today.minusYears(7))
        whenever(marketDataRepo.findEarliestPriceDateByAssetId(asset.id)).thenReturn(today.minusYears(3))
        whenever(marketDataRepo.findLatestPriceDateByAssetId(asset.id)).thenReturn(today)

        service.backFill(asset, today.minusYears(3))

        verify(provider).backFill(eq(asset), eq(today.minusYears(7)))
        verify(cacheInvalidationProducer, never()).sendPriceHistoryEvent(any(), any())
        verify(cacheInvalidationProducer, never()).sendPriceEvent(any())
    }
}