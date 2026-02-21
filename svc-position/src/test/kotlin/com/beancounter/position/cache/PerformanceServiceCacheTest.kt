package com.beancounter.position.cache

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
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.service.PerformanceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PerformanceServiceCacheTest {
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

    @Mock
    private lateinit var cacheService: PerformanceCacheService

    private val twrCalculator = TwrCalculator()
    private val dateUtils = DateUtils()
    private lateinit var performanceService: PerformanceService

    private val usd = Currency("USD")
    private val usMarket = Market("US")
    private val testAsset =
        Asset(
            code = "AAPL",
            id = "aapl-id",
            name = "Apple Inc",
            market = usMarket
        )
    private val owner =
        SystemUser(
            id = "test@test.com",
            email = "test@test.com",
            true,
            since = DateUtils().getFormattedDate("2020-01-01")
        )
    private val portfolio =
        Portfolio(
            id = "test-pf",
            code = "TEST",
            name = "Test Portfolio",
            currency = usd,
            base = usd,
            owner = owner
        )

    @BeforeEach
    fun setup() {
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
                cacheService = cacheService
            )
    }

    @Test
    fun `cache hit skips transaction, price and FX calls`() {
        whenever(cacheService.isAvailable()).thenReturn(true)

        // Return cached snapshots — no transaction fetch needed
        whenever(cacheService.findAllSnapshots(eq(portfolio.id)))
            .thenReturn(
                listOf(
                    CachedSnapshot(
                        valuationDate = LocalDate.now().minusMonths(6),
                        marketValue = BigDecimal("15000"),
                        externalCashFlow = BigDecimal.ZERO,
                        netContributions = BigDecimal.ZERO,
                        cumulativeDividends = BigDecimal.ZERO
                    ),
                    CachedSnapshot(
                        valuationDate = LocalDate.now(),
                        marketValue = BigDecimal("16000"),
                        externalCashFlow = BigDecimal.ZERO,
                        netContributions = BigDecimal.ZERO,
                        cumulativeDividends = BigDecimal.ZERO
                    )
                )
            )

        val result = performanceService.calculate(portfolio, 12)

        assertThat(result.data.series).isNotEmpty()
        // Nothing should have been called — all skipped on cache hit
        verify(trnService, never()).query(any<Portfolio>(), any())
        verify(priceService, never()).getBulkPrices(any(), any())
        verify(fxRateService, never()).getBulkRates(any(), any())
    }

    @Test
    fun `cache hit filters snapshots to requested months window`() {
        whenever(cacheService.isAvailable()).thenReturn(true)

        // Cache has 12 months of data
        val now = LocalDate.now()
        val cachedSnapshots =
            listOf(
                CachedSnapshot(
                    valuationDate = now.minusMonths(12),
                    marketValue = BigDecimal("10000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = now.minusMonths(6),
                    marketValue = BigDecimal("12000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = now.minusMonths(3),
                    marketValue = BigDecimal("13000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = now.minusMonths(1),
                    marketValue = BigDecimal("14000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = now,
                    marketValue = BigDecimal("15000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                )
            )
        whenever(cacheService.findAllSnapshots(eq(portfolio.id)))
            .thenReturn(cachedSnapshots)

        // Request only 3 months — should filter out older snapshots
        val result = performanceService.calculate(portfolio, 3)

        assertThat(result.data.series).isNotEmpty()
        // Should only include snapshots within 3 months of today
        val dates = result.data.series.map { it.date }
        val threeMonthsAgo = now.minusMonths(3)
        assertThat(dates).allSatisfy { date ->
            assertThat(date).isAfterOrEqualTo(threeMonthsAgo)
        }
        // The 12-month and 6-month snapshots should be excluded
        assertThat(dates).doesNotContain(now.minusMonths(12))
        assertThat(dates).doesNotContain(now.minusMonths(6))
    }

    @Test
    fun `cache miss stores results`() {
        val buyDate = LocalDate.now().minusMonths(6)
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = testAsset,
                tradeDate = buyDate,
                quantity = BigDecimal("100"),
                tradeAmount = BigDecimal("15000")
            )
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyTrn)))
        whenever(cacheService.isAvailable()).thenReturn(true)
        whenever(cacheService.findAllSnapshots(eq(portfolio.id)))
            .thenReturn(emptyList())

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

        performanceService.calculate(portfolio, 12)

        // Cache store should have been called
        verify(cacheService).storeSnapshots(eq(portfolio.id), any())
    }

    @Test
    fun `cache failure does not break computation`() {
        val buyDate = LocalDate.now().minusMonths(6)
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = testAsset,
                tradeDate = buyDate,
                quantity = BigDecimal("100"),
                tradeAmount = BigDecimal("15000")
            )
        whenever(cacheService.isAvailable()).thenReturn(true)
        // Cache lookup throws exception
        whenever(cacheService.findAllSnapshots(eq(portfolio.id)))
            .thenThrow(RuntimeException("DB connection failed"))
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyTrn)))

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

        // Should not throw - falls back to full computation
        val result = performanceService.calculate(portfolio, 12)
        assertThat(result.data.series).isNotEmpty()
    }
}