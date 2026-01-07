package com.beancounter.event.service.alpha

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.event.service.TaxService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for AlphaEventAdapter to ensure correct transaction date handling.
 *
 * Critical: SPLIT transactions must use the exact recordDate as tradeDate
 * without any future settlement date calculation (no T+30).
 */
class AlphaEventAdapterTest {
    private lateinit var alphaEventAdapter: AlphaEventAdapter
    private lateinit var taxService: TaxService
    private lateinit var testMarket: Market
    private lateinit var testAsset: Asset
    private lateinit var testPortfolio: Portfolio
    private lateinit var testPosition: Position

    @BeforeEach
    fun setUp() {
        taxService = mock(TaxService::class.java)
        alphaEventAdapter = AlphaEventAdapter(taxService)

        val usd = Currency("USD")
        testMarket = Market("US", currency = usd)
        testAsset =
            Asset(
                id = "test-asset-id",
                code = "XLE",
                market = testMarket,
                name = "Energy Select Sector SPDR"
            )
        testPortfolio =
            Portfolio(
                id = "test-portfolio",
                currency = usd,
                base = usd,
                owner = SystemUser("test-user", "test@example.com")
            )
        testPosition =
            Position(
                asset = testAsset,
                portfolio = testPortfolio
            )
        testPosition.quantityValues = QuantityValues(purchased = BigDecimal("100"))

        `when`(taxService.getRate("USD")).thenReturn(BigDecimal.ZERO)
    }

    @Test
    fun `SPLIT transaction should use exact recordDate as tradeDate without T+30`() {
        // Given: A SPLIT corporate event with a specific record date
        val splitRecordDate = LocalDate.of(2025, 12, 5)
        val splitCorporateEvent =
            CorporateEvent(
                id = "split-event-1",
                assetId = testAsset.id,
                trnType = TrnType.SPLIT,
                recordDate = splitRecordDate,
                split = BigDecimal("2.0"), // 2:1 split
                source = "ALPHA"
            )

        // When: Processing the SPLIT event
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, splitCorporateEvent)

        // Then: The tradeDate should be EXACTLY the recordDate (no T+30)
        assertThat(result.trnInput.trnType).isEqualTo(TrnType.SPLIT)
        assertThat(result.trnInput.tradeDate).isEqualTo(splitRecordDate)
        assertThat(result.trnInput.settleDate).isNull()
    }

    @Test
    fun `SPLIT transaction should not add any days to recordDate`() {
        // Given: A SPLIT event for XLE on December 5, 2025
        val recordDate = LocalDate.of(2025, 12, 5)
        val splitEvent =
            CorporateEvent(
                id = "xle-split",
                assetId = testAsset.id,
                trnType = TrnType.SPLIT,
                recordDate = recordDate,
                split = BigDecimal("2.0"),
                source = "ALPHA"
            )

        // When: Processing the SPLIT event
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, splitEvent)

        // Then: tradeDate should equal recordDate exactly (no days added)
        assertThat(result.trnInput.tradeDate)
            .describedAs("SPLIT tradeDate must equal recordDate without any days added")
            .isEqualTo(recordDate)

        // Explicitly verify no future date calculation occurred
        assertThat(result.trnInput.tradeDate)
            .describedAs("SPLIT should NOT have T+30 or any future settlement")
            .isNotEqualTo(recordDate.plusDays(30))
            .isNotEqualTo(recordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD))
            .isNotEqualTo(recordDate.plusDays(20))
    }

    @Test
    fun `SPLIT transaction should have correct quantity based on split ratio`() {
        // Given: A 2:1 split event
        val splitEvent =
            CorporateEvent(
                id = "split-qty-test",
                assetId = testAsset.id,
                trnType = TrnType.SPLIT,
                recordDate = LocalDate.of(2025, 12, 5),
                split = BigDecimal("2.0"),
                source = "ALPHA"
            )

        // When: Processing the SPLIT event
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, splitEvent)

        // Then: Quantity should reflect the split ratio
        assertThat(result.trnInput.quantity).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(result.trnInput.price).isEqualByComparingTo(BigDecimal("2.0"))
    }

    @Test
    fun `DIVIDEND transaction should use recordDate plus configured days as payDate`() {
        // Given: A DIVIDEND corporate event
        val recordDate = LocalDate.of(2024, 1, 15)
        val expectedPayDate = recordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD)
        val dividendEvent =
            CorporateEvent(
                id = "divi-event",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Processing the DIVIDEND event (with pay date in the past)
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, dividendEvent)

        // Then: The tradeDate should be recordDate + configured days (not like SPLIT)
        if (result.trnInput.trnType != TrnType.IGNORE) {
            assertThat(result.trnInput.tradeDate)
                .describedAs("DIVIDEND tradeDate should be recordDate + configured days")
                .isEqualTo(expectedPayDate)
        }
    }

    @Test
    fun `SPLIT transaction should preserve original recordDate regardless of when processed`() {
        // Given: A historical SPLIT event
        val historicalRecordDate = LocalDate.of(2020, 8, 31) // AAPL split date
        val historicalSplitEvent =
            CorporateEvent(
                id = "historical-split",
                assetId = testAsset.id,
                trnType = TrnType.SPLIT,
                recordDate = historicalRecordDate,
                split = BigDecimal("4.0"), // 4:1 split
                source = "ALPHA"
            )

        // When: Processing the historical SPLIT event today
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, historicalSplitEvent)

        // Then: tradeDate should be the historical recordDate, not today
        assertThat(result.trnInput.tradeDate)
            .describedAs("Historical SPLIT should use original recordDate")
            .isEqualTo(historicalRecordDate)
            .isNotEqualTo(LocalDate.now())
    }

    @Test
    fun `calculatePayDate should return recordDate plus configured days`() {
        // Given: A corporate event with a specific record date
        val recordDate = LocalDate.of(2024, 6, 15)
        val corporateEvent =
            CorporateEvent(
                id = "pay-date-test",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Calculating the pay date
        val payDate = alphaEventAdapter.calculatePayDate(corporateEvent)

        // Then: Pay date should be recordDate + configured days (default 10)
        assertThat(payDate)
            .describedAs("Pay date should be recordDate + configured days")
            .isEqualTo(recordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD))
            .isEqualTo(LocalDate.of(2024, 6, 25))
    }

    @Test
    fun `calculatePayDate should handle month boundaries correctly`() {
        // Given: A record date near the end of a month
        val recordDate = LocalDate.of(2024, 1, 25)
        val corporateEvent =
            CorporateEvent(
                id = "month-boundary-test",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.25"),
                source = "ALPHA"
            )

        // When: Calculating the pay date
        val payDate = alphaEventAdapter.calculatePayDate(corporateEvent)

        // Then: Pay date should cross into February (Jan 25 + 10 days = Feb 4)
        assertThat(payDate)
            .describedAs("Pay date should correctly cross month boundary")
            .isEqualTo(recordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD))
            .isEqualTo(LocalDate.of(2024, 2, 4))
    }

    @Test
    fun `calculatePayDate should handle year boundaries correctly`() {
        // Given: A record date near the end of a year
        val recordDate = LocalDate.of(2024, 12, 25)
        val corporateEvent =
            CorporateEvent(
                id = "year-boundary-test",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.30"),
                source = "ALPHA"
            )

        // When: Calculating the pay date
        val payDate = alphaEventAdapter.calculatePayDate(corporateEvent)

        // Then: Pay date should be in January of the next year (Dec 25 + 10 days = Jan 4)
        assertThat(payDate)
            .describedAs("Pay date should correctly cross year boundary")
            .isEqualTo(recordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD))
            .isEqualTo(LocalDate.of(2025, 1, 4))
    }

    @Test
    fun `DIVIDEND should use overridePayDate when provided`() {
        // Given: A DIVIDEND event with an override pay date
        val recordDate = LocalDate.of(2024, 1, 15)
        val overridePayDate = "2024-02-01" // Custom pay date
        val dividendEvent =
            CorporateEvent(
                id = "divi-override",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Processing the DIVIDEND event with override pay date
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, dividendEvent, overridePayDate)

        // Then: The tradeDate should be the overridden date
        if (result.trnInput.trnType != TrnType.IGNORE) {
            assertThat(result.trnInput.tradeDate)
                .describedAs("DIVIDEND tradeDate should use override pay date when provided")
                .isEqualTo(LocalDate.of(2024, 2, 1))
        }
    }

    @Test
    fun `DIVIDEND should use calculated payDate when override is null`() {
        // Given: A DIVIDEND event without override pay date
        val recordDate = LocalDate.of(2024, 1, 15)
        val expectedPayDate = recordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD)
        val dividendEvent =
            CorporateEvent(
                id = "divi-no-override",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Processing the DIVIDEND event without override
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, dividendEvent, null)

        // Then: The tradeDate should be the calculated default (recordDate + configured days)
        if (result.trnInput.trnType != TrnType.IGNORE) {
            assertThat(result.trnInput.tradeDate)
                .describedAs("DIVIDEND tradeDate should use calculated pay date when no override provided")
                .isEqualTo(expectedPayDate)
        }
    }

    @Test
    fun `SPLIT should ignore overridePayDate parameter`() {
        // Given: A SPLIT event with an override pay date (should be ignored)
        val recordDate = LocalDate.of(2024, 6, 15)
        val overridePayDate = "2024-07-01" // This should be ignored for SPLIT
        val splitEvent =
            CorporateEvent(
                id = "split-override-ignored",
                assetId = testAsset.id,
                trnType = TrnType.SPLIT,
                recordDate = recordDate,
                split = BigDecimal("2.0"),
                source = "ALPHA"
            )

        // When: Processing the SPLIT event with override pay date
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, splitEvent, overridePayDate)

        // Then: The tradeDate should still be the recordDate (override ignored)
        assertThat(result.trnInput.tradeDate)
            .describedAs("SPLIT should ignore overridePayDate and use recordDate")
            .isEqualTo(recordDate)
            .isNotEqualTo(LocalDate.of(2024, 7, 1))
    }

    @Test
    fun `daysToAdd configured to 1 should calculate payDate as recordDate plus 1 day`() {
        // Given: An adapter configured with daysToAdd = 1
        val adapterWithOneDay = AlphaEventAdapter(taxService, daysToAdd = 1)
        val recordDate = LocalDate.of(2024, 6, 15)
        val corporateEvent =
            CorporateEvent(
                id = "one-day-test",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Calculating the pay date
        val payDate = adapterWithOneDay.calculatePayDate(corporateEvent)

        // Then: Pay date should be recordDate + 1 day
        assertThat(payDate)
            .describedAs("Pay date should be recordDate + 1 day when configured with daysToAdd=1")
            .isEqualTo(recordDate.plusDays(1))
            .isEqualTo(LocalDate.of(2024, 6, 16))
    }

    @Test
    fun `daysToAdd can be configured to custom value`() {
        // Given: An adapter configured with daysToAdd = 30
        val adapterWith30Days = AlphaEventAdapter(taxService, daysToAdd = 30)
        val recordDate = LocalDate.of(2024, 6, 1)
        val corporateEvent =
            CorporateEvent(
                id = "custom-days-test",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Calculating the pay date
        val payDate = adapterWith30Days.calculatePayDate(corporateEvent)

        // Then: Pay date should be recordDate + 30 days
        assertThat(payDate)
            .describedAs("Pay date should be recordDate + 30 days when configured with daysToAdd=30")
            .isEqualTo(recordDate.plusDays(30))
            .isEqualTo(LocalDate.of(2024, 7, 1))
    }

    @Test
    fun `DIVIDEND with daysToAdd=1 should use recordDate plus 1 day as tradeDate`() {
        // Given: An adapter configured with daysToAdd = 1
        val adapterWithOneDay = AlphaEventAdapter(taxService, daysToAdd = 1)
        val recordDate = LocalDate.of(2024, 1, 15)
        val dividendEvent =
            CorporateEvent(
                id = "divi-one-day",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Processing the DIVIDEND event
        val result = adapterWithOneDay.calculate(testPortfolio, testPosition, dividendEvent)

        // Then: The tradeDate should be recordDate + 1 day
        if (result.trnInput.trnType != TrnType.IGNORE) {
            assertThat(result.trnInput.tradeDate)
                .describedAs("DIVIDEND tradeDate should be recordDate + 1 day when daysToAdd=1")
                .isEqualTo(recordDate.plusDays(1))
                .isEqualTo(LocalDate.of(2024, 1, 16))
        }
    }

    @Test
    fun `future dated DIVIDEND should be created as PROPOSED transaction`() {
        // Given: A DIVIDEND event with a record date that results in a future pay date
        val futureRecordDate = LocalDate.now().plusDays(5)
        val expectedPayDate = futureRecordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD)
        val dividendEvent =
            CorporateEvent(
                id = "future-divi",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = futureRecordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Processing the future-dated DIVIDEND event
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, dividendEvent)

        // Then: The transaction should be created as PROPOSED (not IGNORE)
        assertThat(result.trnInput.trnType)
            .describedAs("Future-dated dividends should be created as DIVI, not IGNORE")
            .isEqualTo(TrnType.DIVI)

        assertThat(result.trnInput.status)
            .describedAs("Future-dated dividends should have PROPOSED status")
            .isEqualTo(TrnStatus.PROPOSED)

        assertThat(result.trnInput.tradeDate)
            .describedAs("Future-dated dividends should have the calculated pay date as tradeDate")
            .isEqualTo(expectedPayDate)
    }

    @Test
    fun `DIVIDEND with pay date far in the future should still be PROPOSED`() {
        // Given: A DIVIDEND event with a record date far in the future (next year)
        val farFutureRecordDate = LocalDate.now().plusMonths(6)
        val expectedPayDate = farFutureRecordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD)
        val dividendEvent =
            CorporateEvent(
                id = "far-future-divi",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = farFutureRecordDate,
                rate = BigDecimal("0.75"),
                source = "ALPHA"
            )

        // When: Processing the far future DIVIDEND event
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, dividendEvent)

        // Then: The transaction should still be created as PROPOSED
        assertThat(result.trnInput.trnType)
            .describedAs("Far future dividends should be created as DIVI, not IGNORE")
            .isEqualTo(TrnType.DIVI)

        assertThat(result.trnInput.status)
            .describedAs("Far future dividends should have PROPOSED status")
            .isEqualTo(TrnStatus.PROPOSED)

        assertThat(result.trnInput.tradeDate)
            .describedAs("Far future dividends should have the calculated pay date")
            .isEqualTo(expectedPayDate)
    }

    @Test
    fun `past dated DIVIDEND should also be PROPOSED`() {
        // Given: A DIVIDEND event with a past record date
        val pastRecordDate = LocalDate.of(2024, 1, 15)
        val expectedPayDate = pastRecordDate.plusDays(AlphaEventAdapter.DEFAULT_DAYS_TO_ADD)
        val dividendEvent =
            CorporateEvent(
                id = "past-divi",
                assetId = testAsset.id,
                trnType = TrnType.DIVI,
                recordDate = pastRecordDate,
                rate = BigDecimal("0.50"),
                source = "ALPHA"
            )

        // When: Processing the past DIVIDEND event
        val result = alphaEventAdapter.calculate(testPortfolio, testPosition, dividendEvent)

        // Then: Past dividends should still be PROPOSED (user can manually settle)
        assertThat(result.trnInput.trnType)
            .describedAs("Past dividends should be created as DIVI")
            .isEqualTo(TrnType.DIVI)

        assertThat(result.trnInput.status)
            .describedAs("Past dividends should have PROPOSED status")
            .isEqualTo(TrnStatus.PROPOSED)

        assertThat(result.trnInput.tradeDate)
            .describedAs("Past dividends should have the calculated pay date")
            .isEqualTo(expectedPayDate)
    }
}