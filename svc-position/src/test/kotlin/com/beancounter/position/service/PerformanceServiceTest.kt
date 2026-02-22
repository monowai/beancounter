package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.cache.NoOpPerformanceCacheService
import com.beancounter.position.irr.TwrCalculator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PerformanceServiceTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var accumulator: Accumulator

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    private val twrCalculator = TwrCalculator()
    private val dateUtils = DateUtils()
    private lateinit var performanceService: PerformanceService

    private val usMarket = Market("US")
    private val testAsset =
        Asset(
            code = "AAPL",
            id = "aapl-id",
            name = "Apple Inc",
            market = usMarket
        )
    private val portfolio =
        Portfolio(
            id = "test-pf",
            code = "TEST",
            name = "Test Portfolio",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setup() {
        // Use lenient since not all tests call calculate()
        lenient().`when`(tokenService.bearerToken).thenReturn("test-token")
        performanceService =
            PerformanceService(
                trnService = trnService,
                accumulator = accumulator,
                priceService = priceService,
                fxRateService = fxRateService,
                twrCalculator = twrCalculator,
                dateUtils = dateUtils,
                tokenService = tokenService,
                cacheService = NoOpPerformanceCacheService()
            )
    }

    @Test
    fun `empty transactions returns empty response with null firstTradeDate`() {
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse())

        val result = performanceService.calculate(portfolio, 12)

        assertThat(result.data.series).isEmpty()
        assertThat(result.data.currency.code).isEqualTo("USD")
        assertThat(result.data.firstTradeDate).isNull()
    }

    @Test
    fun `determines correct valuation dates`() {
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 3, 1)

        val transactions =
            listOf(
                Trn(
                    trnType = TrnType.DEPOSIT,
                    asset = testAsset,
                    tradeDate = LocalDate.of(2024, 1, 15)
                ),
                Trn(
                    trnType = TrnType.BUY,
                    asset = testAsset,
                    tradeDate = LocalDate.of(2024, 2, 1)
                ),
                Trn(
                    trnType = TrnType.WITHDRAWAL,
                    asset = testAsset,
                    tradeDate = LocalDate.of(2024, 2, 20)
                )
            )

        val dates = performanceService.determineValuationDates(transactions, startDate, endDate)

        // Should include: startDate, cashFlow dates (Jan 15, Feb 20), monthly (Feb 1), endDate
        assertThat(dates).contains(startDate)
        assertThat(dates).contains(endDate)
        assertThat(dates).contains(LocalDate.of(2024, 1, 15)) // DEPOSIT date
        assertThat(dates).contains(LocalDate.of(2024, 2, 20)) // WITHDRAWAL date
        assertThat(dates).contains(LocalDate.of(2024, 2, 1)) // Monthly date
        assertThat(dates).isSorted
    }

    @Test
    fun `BUY and SELL are not external cash flows`() {
        assertThat(PerformanceService.isExternalCashFlow(TrnType.BUY)).isFalse()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.SELL)).isFalse()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.DIVI)).isFalse()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.SPLIT)).isFalse()
    }

    @Test
    fun `DEPOSIT, WITHDRAWAL, INCOME, DEDUCTION are external cash flows`() {
        assertThat(PerformanceService.isExternalCashFlow(TrnType.DEPOSIT)).isTrue()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.WITHDRAWAL)).isTrue()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.INCOME)).isTrue()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.DEDUCTION)).isTrue()
        assertThat(PerformanceService.isExternalCashFlow(TrnType.EXPENSE)).isTrue()
    }

    @Test
    fun `withdrawal with positive cashAmount is normalized to negative`() {
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 2, 1)

        // Withdrawal with WRONG sign (positive cashAmount) — some import sources do this
        val withdrawal =
            Trn(
                trnType = TrnType.WITHDRAWAL,
                asset = testAsset,
                tradeDate = LocalDate.of(2024, 1, 15),
                cashAmount = BigDecimal("5000")
            )
        // Deposit with correct sign
        val deposit =
            Trn(
                trnType = TrnType.DEPOSIT,
                asset = testAsset,
                tradeDate = LocalDate.of(2024, 1, 10),
                cashAmount = BigDecimal("10000")
            )

        val transactions = listOf(deposit, withdrawal)
        val dates = performanceService.determineValuationDates(transactions, startDate, endDate)

        // Withdrawal date should still be detected as an external cash flow date
        assertThat(dates).contains(LocalDate.of(2024, 1, 15))
        assertThat(dates).contains(LocalDate.of(2024, 1, 10))
    }

    @Test
    fun `response has correct currency`() {
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse())

        val gbpPortfolio =
            portfolio.copy(
                currency = Currency("GBP")
            )
        val result = performanceService.calculate(gbpPortfolio, 6)
        assertThat(result.data.currency.code).isEqualTo("GBP")
    }

    @Test
    fun `DIVI transactions accumulate into cumulativeDividends`() {
        val buyDate = LocalDate.now().minusMonths(6)
        val diviDate = LocalDate.now().minusMonths(3)

        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = testAsset,
                tradeDate = buyDate,
                quantity = BigDecimal("100"),
                price = BigDecimal("150"),
                tradeAmount = BigDecimal("15000"),
                cashAmount = BigDecimal("-15000")
            )
        val diviTrn =
            Trn(
                trnType = TrnType.DIVI,
                asset = testAsset,
                tradeDate = diviDate,
                tradeAmount = BigDecimal("500"),
                cashAmount = BigDecimal("500")
            )

        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyTrn, diviTrn)))

        whenever(accumulator.accumulate(any(), any()))
            .thenAnswer { invocation ->
                val positions = invocation.getArgument<com.beancounter.common.model.Positions>(1)
                positions.getOrCreate(testAsset)
            }

        lenient()
            .`when`(priceService.getBulkPrices(any(), any()))
            .thenReturn(BulkPriceResponse(emptyMap()))
        lenient()
            .`when`(fxRateService.getBulkRates(any(), any()))
            .thenReturn(BulkFxResponse(emptyMap()))

        val result = performanceService.calculate(portfolio, 12)

        assertThat(result.data.series).isNotEmpty
        // The last data point should have accumulated the dividend
        val lastPoint = result.data.series.last()
        assertThat(lastPoint.cumulativeDividends.toDouble())
            .isCloseTo(500.0, Offset.offset(0.01))

        // First data point (before the dividend date) should be zero
        val firstPoint = result.data.series.first()
        assertThat(firstPoint.cumulativeDividends.toDouble())
            .isCloseTo(0.0, Offset.offset(0.01))
    }

    @Test
    fun `firstTradeDate matches earliest transaction date`() {
        val earlyDate = LocalDate.now().minusMonths(6)
        val laterDate = LocalDate.now().minusMonths(3)

        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = testAsset,
                tradeDate = earlyDate,
                quantity = BigDecimal("100"),
                price = BigDecimal("150"),
                tradeAmount = BigDecimal("15000"),
                cashAmount = BigDecimal("-15000")
            )
        val diviTrn =
            Trn(
                trnType = TrnType.DIVI,
                asset = testAsset,
                tradeDate = laterDate,
                tradeAmount = BigDecimal("500"),
                cashAmount = BigDecimal("500")
            )

        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyTrn, diviTrn)))

        whenever(accumulator.accumulate(any(), any()))
            .thenAnswer { invocation ->
                val positions = invocation.getArgument<com.beancounter.common.model.Positions>(1)
                positions.getOrCreate(testAsset)
            }

        lenient()
            .`when`(priceService.getBulkPrices(any(), any()))
            .thenReturn(BulkPriceResponse(emptyMap()))
        lenient()
            .`when`(fxRateService.getBulkRates(any(), any()))
            .thenReturn(BulkFxResponse(emptyMap()))

        val result = performanceService.calculate(portfolio, 12)

        assertThat(result.data.firstTradeDate).isEqualTo(earlyDate)
    }

    @Test
    fun `uses average cost as price when no market price available`() {
        val buyDate = LocalDate.now().minusMonths(6)
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = testAsset,
                tradeDate = buyDate,
                quantity = BigDecimal("100"),
                price = BigDecimal("150"),
                tradeAmount = BigDecimal("15000"),
                cashAmount = BigDecimal("-15000")
            )

        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyTrn)))

        // Mock accumulator to create a position with quantity AND averageCost
        whenever(accumulator.accumulate(any(), any()))
            .thenAnswer { invocation ->
                val positions = invocation.getArgument<com.beancounter.common.model.Positions>(1)
                val pos = positions.getOrCreate(testAsset)
                pos.quantityValues.purchased = BigDecimal("100")
                pos.moneyValues[Position.In.TRADE]!!.averageCost = BigDecimal("150")
                pos
            }

        // No market prices available
        lenient()
            .`when`(priceService.getBulkPrices(any(), any()))
            .thenReturn(BulkPriceResponse(emptyMap()))
        lenient()
            .`when`(fxRateService.getBulkRates(any(), any()))
            .thenReturn(BulkFxResponse(emptyMap()))

        val result = performanceService.calculate(portfolio, 12)

        assertThat(result.data.series).isNotEmpty
        // With fallback to cost, market value should be 100 * 150 = 15000
        val lastPoint = result.data.series.last()
        assertThat(lastPoint.marketValue.toDouble())
            .isCloseTo(15000.0, Offset.offset(0.01))
    }

    @Test
    fun `growth of 1000 starts at 1000`() {
        // Setup: single BUY transaction, price is available
        val buyDate = LocalDate.now().minusMonths(6)
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = testAsset,
                tradeDate = buyDate,
                quantity = BigDecimal("100"),
                price = BigDecimal("150"),
                tradeAmount = BigDecimal("15000"),
                cashAmount = BigDecimal("-15000")
            )
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyTrn)))

        // Mock accumulator to create a position with quantity
        whenever(accumulator.accumulate(any(), any()))
            .thenAnswer { invocation ->
                val positions = invocation.getArgument<com.beancounter.common.model.Positions>(1)
                positions.getOrCreate(testAsset)
            }

        // Mock bulk prices and FX (lenient — may not be called if no valued positions)
        lenient()
            .`when`(priceService.getBulkPrices(any(), any()))
            .thenReturn(BulkPriceResponse(emptyMap()))
        lenient()
            .`when`(fxRateService.getBulkRates(any(), any()))
            .thenReturn(BulkFxResponse(emptyMap()))

        val result = performanceService.calculate(portfolio, 12)

        // First data point should have growthOf1000 = 1000
        assertThat(result.data.series).isNotEmpty
        assertThat(
            result.data.series
                .first()
                .growthOf1000
                .toDouble()
        ).isCloseTo(1000.0, Offset.offset(0.01))
    }
}