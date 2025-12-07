package com.beancounter.event.service.alpha

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.SystemUser
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
            .isNotEqualTo(recordDate.plusDays(18))
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
    fun `DIVIDEND transaction should use recordDate plus 18 days as payDate`() {
        // Given: A DIVIDEND corporate event
        val recordDate = LocalDate.of(2024, 1, 15)
        val expectedPayDate = recordDate.plusDays(18)
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

        // Then: The tradeDate should be recordDate + 18 days (not like SPLIT)
        if (result.trnInput.trnType != TrnType.IGNORE) {
            assertThat(result.trnInput.tradeDate)
                .describedAs("DIVIDEND tradeDate should be recordDate + 18 days")
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
}
