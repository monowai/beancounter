package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.PriceHistoryResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.price.MarketDataService
import com.beancounter.marketdata.price.history.PriceHistoryReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/** Suggested opening dates for a statement's rows, from each asset's stored price history. */
class HeldSinceServiceTest {
    private val priceHistoryReader = mock<PriceHistoryReader>()
    private val marketDataService = mock<MarketDataService>()
    private val dateUtils = DateUtils()
    private val service = HeldSinceService(priceHistoryReader, marketDataService, dateUtils)

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

        assertThat(suggestions).extracting<Any?> { it.heldSince }.containsOnly(today.minusDays(2))
        verify(priceHistoryReader, times(1)).getPriceHistory(eq(asset.id), any(), any())
    }
}