package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.PriceHistoryResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.price.MarketDataService
import com.beancounter.marketdata.price.history.PriceHistoryReader
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.MAX_BACKFILL_YEARS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/** Suggested opening dates for a statement's rows, from each asset's price history. */
class HeldSinceServiceTest {
    private val priceHistoryReader = mock<PriceHistoryReader>()
    private val marketDataService = mock<MarketDataService>()
    private val providerHistory = mock<ProviderHistory>()
    private val dateUtils = DateUtils()
    private val service = HeldSinceService(priceHistoryReader, marketDataService, providerHistory, dateUtils)

    private val asset =
        Asset(
            id = "asset-1",
            code = "ABC",
            name = "ABC",
            market = Market("NASDAQ", currencyId = "USD"),
            marketCode = "NASDAQ",
            category = "Equity"
        )

    @Test
    fun `should read an asset's history once when the statement lists it in two rows`() {
        val today = dateUtils.date
        val prices =
            listOf(
                PricePoint(priceDate = today.minusDays(2), close = BigDecimal("8")),
                PricePoint(priceDate = today.minusDays(1), close = BigDecimal("20"))
            )
        whenever(priceHistoryReader.getPriceHistory(eq(asset.id), any(), any()))
            .thenReturn(PriceHistoryResponse(asset, prices))

        val suggestions =
            service.suggest(
                HeldSinceRequest(
                    listOf(
                        HeldSinceQuery(asset.id, BigDecimal("10")),
                        HeldSinceQuery(asset.id, BigDecimal("10"))
                    )
                )
            )

        assertThat(suggestions).extracting<Any?> { it.heldSince }.containsOnly(today.minusDays(1))
        verify(priceHistoryReader, times(1)).getPriceHistory(eq(asset.id), any(), any())
    }

    @Test
    fun `should fill the history back to the backfill limit even when some prices are stored`() {
        val today = dateUtils.date
        whenever(priceHistoryReader.getPriceHistory(eq(asset.id), any(), any())).thenReturn(
            PriceHistoryResponse(asset, listOf(PricePoint(priceDate = today.minusDays(1), close = BigDecimal("10"))))
        )

        service.suggest(HeldSinceRequest(listOf(HeldSinceQuery(asset.id, BigDecimal("10")))))

        verify(marketDataService).backFill(asset.id, today.minusYears(MAX_BACKFILL_YEARS))
    }

    @Test
    fun `should ask the provider for older prices when the cost is below every price in the window`() {
        val today = dateUtils.date
        val earliest = today.minusYears(MAX_BACKFILL_YEARS)
        whenever(priceHistoryReader.getPriceHistory(eq(asset.id), any(), any())).thenReturn(
            PriceHistoryResponse(asset, listOf(PricePoint(priceDate = earliest, close = BigDecimal("50"))))
        )
        val older = LocalDate.of(2005, 1, 3)
        whenever(providerHistory.closesBefore(asset.id, earliest))
            .thenReturn(listOf(older.minusDays(1) to BigDecimal("4"), older to BigDecimal("10.5")))

        val suggestion = service.suggest(HeldSinceRequest(listOf(HeldSinceQuery(asset.id, BigDecimal("10"))))).single()

        assertThat(suggestion.heldSince).isEqualTo(older)
        assertThat(suggestion.before).isNull()
    }

    @Test
    fun `should not ask the provider for older prices when the window has a date`() {
        val today = dateUtils.date
        whenever(priceHistoryReader.getPriceHistory(eq(asset.id), any(), any())).thenReturn(
            PriceHistoryResponse(asset, listOf(PricePoint(priceDate = today.minusDays(1), close = BigDecimal("10"))))
        )

        service.suggest(HeldSinceRequest(listOf(HeldSinceQuery(asset.id, BigDecimal("10")))))

        verify(providerHistory, never()).closesBefore(any(), any())
    }

    @Test
    fun `should keep the window's answer when the provider has nothing older`() {
        val today = dateUtils.date
        val earliest = today.minusYears(MAX_BACKFILL_YEARS)
        whenever(priceHistoryReader.getPriceHistory(eq(asset.id), any(), any())).thenReturn(
            PriceHistoryResponse(asset, listOf(PricePoint(priceDate = earliest, close = BigDecimal("50"))))
        )
        whenever(providerHistory.closesBefore(asset.id, earliest)).thenThrow(IllegalStateException("provider down"))

        val suggestion = service.suggest(HeldSinceRequest(listOf(HeldSinceQuery(asset.id, BigDecimal("10"))))).single()

        assertThat(suggestion.before).isEqualTo(earliest)
    }
}