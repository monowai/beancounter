package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import java.time.LocalDate

/**
 * Tests for PortfolioPriceBackfillService.
 */
class PortfolioPriceBackfillServiceTest {
    private lateinit var service: PortfolioPriceBackfillService
    private lateinit var portfolioService: PortfolioService
    private lateinit var trnService: TrnService
    private lateinit var priceProcessor: MarketDataPriceProcessor
    private lateinit var providerUtils: ProviderUtils
    private lateinit var dateUtils: DateUtils

    private val portfolio = Portfolio(id = "p1", code = "TEST", name = "Test Portfolio")
    private val today = LocalDate.of(2025, 6, 15)

    private val assetAapl = Asset(code = "AAPL", id = "aapl-id", market = NASDAQ)
    private val assetMsft = Asset(code = "MSFT", id = "msft-id", market = NYSE)
    private val cashAsset =
        Asset(
            code = "USD",
            id = "usd-id",
            market = NASDAQ,
            assetCategory = AssetCategory(AssetCategory.CASH, "Cash")
        )

    @BeforeEach
    fun setUp() {
        portfolioService = mock(PortfolioService::class.java)
        trnService = mock(TrnService::class.java)
        priceProcessor = mock(MarketDataPriceProcessor::class.java)
        providerUtils = mock(ProviderUtils::class.java)
        dateUtils = mock(DateUtils::class.java)

        service =
            PortfolioPriceBackfillService(
                portfolioService,
                trnService,
                priceProcessor,
                providerUtils,
                dateUtils,
                CashUtils()
            )

        `when`(portfolioService.findByCode("TEST")).thenReturn(portfolio)
        `when`(dateUtils.date).thenReturn(today)
    }

    @Test
    fun `should return no_transactions when portfolio has no transactions`() {
        `when`(trnService.findForPortfolio(portfolio, today)).thenReturn(emptyList())

        val result = service.backfill("TEST")

        assertThat(result["status"]).isEqualTo("no_transactions")
        assertThat(result["portfolio"]).isEqualTo("TEST")
        verify(priceProcessor, never()).getPriceResponse(any())
    }

    @Test
    fun `should return no_assets when portfolio has only cash transactions`() {
        val cashTrn =
            Trn(
                trnType = TrnType.DEPOSIT,
                asset = cashAsset,
                tradeDate = LocalDate.of(2025, 1, 15)
            )
        `when`(trnService.findForPortfolio(portfolio, today)).thenReturn(listOf(cashTrn))

        val result = service.backfill("TEST")

        assertThat(result["status"]).isEqualTo("no_assets")
        verify(priceProcessor, never()).getPriceResponse(any())
    }

    @Test
    fun `should backfill prices for portfolio with transactions`() {
        val trn1 =
            Trn(
                trnType = TrnType.BUY,
                asset = assetAapl,
                tradeDate = LocalDate.of(2025, 1, 15)
            )
        val trn2 =
            Trn(
                trnType = TrnType.BUY,
                asset = assetMsft,
                tradeDate = LocalDate.of(2025, 3, 10)
            )
        `when`(trnService.findForPortfolio(portfolio, today)).thenReturn(listOf(trn1, trn2))
        `when`(providerUtils.getInputs(any())).thenReturn(emptyList())
        `when`(priceProcessor.getPriceResponse(any())).thenReturn(PriceResponse(emptyList()))

        val result = service.backfill("TEST")

        assertThat(result["status"]).isEqualTo("ok")
        assertThat(result["portfolio"]).isEqualTo("TEST")
        assertThat(result["assetsProcessed"]).isEqualTo(2)
        assertThat(result["datesProcessed"] as Int).isGreaterThan(0)
    }

    @Test
    fun `should include external cash flow dates in valuation dates`() {
        val startDate = LocalDate.of(2025, 1, 15)
        val endDate = LocalDate.of(2025, 4, 15)
        val depositDate = LocalDate.of(2025, 2, 20)

        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = assetAapl,
                tradeDate = startDate
            )
        val depositTrn =
            Trn(
                trnType = TrnType.DEPOSIT,
                asset = cashAsset,
                tradeDate = depositDate
            )

        val dates = service.determineValuationDates(listOf(buyTrn, depositTrn), startDate, endDate)

        assertThat(dates).contains(startDate, endDate, depositDate)
        // Monthly dates: Feb 1, Mar 1, Apr 1
        assertThat(dates).contains(
            LocalDate.of(2025, 2, 1),
            LocalDate.of(2025, 3, 1),
            LocalDate.of(2025, 4, 1)
        )
        // Should be sorted
        assertThat(dates).isSorted()
        // No duplicates
        assertThat(dates).doesNotHaveDuplicates()
    }

    @Test
    fun `should deduplicate assets from multiple transactions`() {
        val trn1 =
            Trn(
                trnType = TrnType.BUY,
                asset = assetAapl,
                tradeDate = LocalDate.of(2025, 1, 15)
            )
        val trn2 =
            Trn(
                trnType = TrnType.BUY,
                asset = assetAapl,
                tradeDate = LocalDate.of(2025, 3, 10)
            )
        `when`(trnService.findForPortfolio(portfolio, today)).thenReturn(listOf(trn1, trn2))
        `when`(providerUtils.getInputs(any())).thenReturn(emptyList())
        `when`(priceProcessor.getPriceResponse(any())).thenReturn(PriceResponse(emptyList()))

        val result = service.backfill("TEST")

        assertThat(result["assetsProcessed"]).isEqualTo(1)
    }
}