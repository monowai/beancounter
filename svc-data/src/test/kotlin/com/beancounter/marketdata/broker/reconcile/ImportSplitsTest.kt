package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.price.MarketDataRepo
import com.beancounter.marketdata.price.SplitRow
import com.beancounter.marketdata.price.history.PriceSplits
import com.beancounter.marketdata.providers.EventServiceFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/** What one share held on a past date has become by the record date. */
class ImportSplitsTest {
    private val marketDataRepo = mock<MarketDataRepo>()
    private val assetFinder = mock<AssetFinder>()
    private val priceSplits = mock<PriceSplits>()
    private val eventServiceFacade = mock<EventServiceFacade>()
    private val splits = ImportSplits(marketDataRepo, assetFinder, priceSplits, eventServiceFacade)
    private val asset =
        Asset(
            id = "goog",
            code = "GOOG",
            name = "GOOG",
            market = Market("NASDAQ", currencyId = "USD"),
            marketCode = "NASDAQ",
            category = "Equity"
        )
    private val from = LocalDate.of(2013, 10, 29)
    private val to = LocalDate.of(2026, 11, 18)

    private fun stored(factor: String) {
        whenever(assetFinder.findOrNull(asset.id)).thenReturn(asset)
        whenever(priceSplits.splitFactorsAfter(mapOf(asset to listOf(from.minusDays(1))), to.minusDays(1)))
            .thenReturn(mapOf(asset.id to mapOf(from.minusDays(1) to BigDecimal(factor))))
    }

    private fun row(date: LocalDate) =
        object : SplitRow {
            override val assetId = asset.id
            override val priceDate = date
            override val split = BigDecimal.ONE
        }

    private fun split(
        date: LocalDate,
        ratio: String
    ) = MarketData(asset, date, BigDecimal.ZERO, split = BigDecimal(ratio))

    @Test
    fun `should take the stored factor when stored history reaches back to the trade date`() {
        stored("40")
        whenever(marketDataRepo.findLatestByAssetInAndPriceDateLessThanEqual(listOf(asset), from))
            .thenReturn(listOf(MarketData(asset, from.minusDays(1), BigDecimal("1000"))))

        assertThat(splits.factor(asset.id, from, to)).isEqualByComparingTo("40")
        verify(eventServiceFacade, never()).getEvents(any())
    }

    @Test
    fun `should add the provider's splits from before the stored history, each once`() {
        val storedFrom = LocalDate.of(2016, 11, 18)
        stored("20")
        whenever(
            marketDataRepo.findLatestByAssetInAndPriceDateLessThanEqual(listOf(asset), from)
        ).thenReturn(emptyList())
        whenever(
            marketDataRepo.findSplitHistory(listOf(asset.id), from, to.minusDays(1))
        ).thenReturn(listOf(row(storedFrom)))
        whenever(eventServiceFacade.getEvents(asset.id)).thenReturn(
            PriceResponse(
                listOf(
                    split(LocalDate.of(2010, 1, 4), "7"),
                    split(LocalDate.of(2014, 3, 27), "2"),
                    split(LocalDate.of(2022, 7, 18), "20")
                )
            )
        )

        assertThat(splits.factor(asset.id, from, to)).isEqualByComparingTo("40")
    }

    @Test
    fun `should say the factor is unknown when the provider cannot be asked`() {
        stored("1")
        whenever(
            marketDataRepo.findLatestByAssetInAndPriceDateLessThanEqual(listOf(asset), from)
        ).thenReturn(emptyList())
        whenever(marketDataRepo.findSplitHistory(listOf(asset.id), from, to.minusDays(1))).thenReturn(emptyList())
        whenever(eventServiceFacade.getEvents(asset.id)).thenThrow(IllegalStateException("provider down"))

        assertThat(splits.factor(asset.id, from, to)).isNull()
    }

    @Test
    fun `should count no split for an asset Beancounter does not have`() {
        assertThat(splits.factor("unknown", from, to)).isEqualByComparingTo("1")
    }
}