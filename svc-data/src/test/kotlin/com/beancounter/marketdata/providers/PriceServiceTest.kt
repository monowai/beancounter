package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.CashUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.event.EventProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Test the PriceService using Mocks
 */
class PriceServiceTest {
    private lateinit var priceService: PriceService
    private lateinit var marketDataRepo: MarketDataRepo
    private lateinit var cashUtils: CashUtils
    private lateinit var eventProducer: EventProducer
    private val asset = Asset(code = "1", market = NASDAQ)

    @BeforeEach
    fun setUp() {
        marketDataRepo = mock(MarketDataRepo::class.java)
        cashUtils = mock(CashUtils::class.java)
        eventProducer = mock(EventProducer::class.java)
        priceService = PriceService(marketDataRepo, cashUtils)
        priceService.setEventWriter(eventProducer)
    }

    @Test
    fun `test getMarketData with existing data`() {
        val date = LocalDate.now()
        val marketData = MarketData(asset, date, BigDecimal.TEN)

        `when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)).thenReturn(Optional.of(marketData))

        val result = priceService.getMarketData(asset, date)

        assertTrue(result.isPresent)
        assertEquals(marketData, result.get())
    }

    @Test
    fun `test getMarketData with non-existing data`() {
        val date = LocalDate.now()

        `when`(marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)).thenReturn(Optional.empty())

        val result = priceService.getMarketData(asset, date)

        assertFalse(result.isPresent)
    }

    @Test
    fun `test handle no close price is not saved`() {
        val localDate = LocalDate.now()
        val marketData = MarketData(asset, LocalDate.now(), BigDecimal.ZERO)
        val priceResponse = PriceResponse(listOf(marketData))

        `when`(cashUtils.isCash(asset)).thenReturn(false)
        `when`(
            marketDataRepo.findByAssetIdAndPriceDate(
                "1",
                localDate
            )
        ).thenReturn(Optional.empty())
        `when`(marketDataRepo.saveAll(anyList())).thenReturn(listOf(marketData))

        val result = priceService.handle(priceResponse)
        verify(marketDataRepo, times(0)).saveAll(anyList())
        assertEquals(0, result.count())
    }

    @Test
    fun `test purge all market data`() {
        priceService.purge()

        verify(marketDataRepo, times(1)).deleteAll()
    }

    @Test
    fun `test purge specific market data`() {
        val marketData = MarketData(asset, LocalDate.now(), BigDecimal.TEN)

        priceService.purge(marketData)

        verify(marketDataRepo, times(1)).deleteById(marketData.id)
    }
}