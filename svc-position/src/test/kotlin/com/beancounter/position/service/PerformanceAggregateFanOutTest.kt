package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.EnsureHistoryRequest
import com.beancounter.common.contracts.EnsureHistoryResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.cache.NoOpPerformanceCacheService
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.irr.TwrCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * How [PerformanceService.aggregate] fans out across portfolios: concurrently, and with
 * a single price-history nudge to svc-data rather than one per portfolio.
 */
@ExtendWith(MockitoExtension::class)
class PerformanceAggregateFanOutTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    private lateinit var service: PerformanceService

    private val dateUtils = DateUtils()

    @BeforeEach
    fun setup() {
        lenient().`when`(tokenService.bearerToken).thenReturn("test-token")
        service =
            PerformanceService(
                trnService = trnService,
                accumulator = Accumulator(TrnBehaviourFactory(listOf(BuyBehaviour()))),
                priceService = priceService,
                fxRateService = fxRateService,
                twrCalculator = TwrCalculator(),
                irrCalculator = IrrCalculator(minHoldingDays = 365, dateUtils = dateUtils),
                dateUtils = dateUtils,
                tokenService = tokenService,
                cacheService = NoOpPerformanceCacheService()
            )
    }

    @Test
    fun `should calculate portfolios concurrently`() {
        val portfolios = listOf(portfolio("P1"), portfolio("P2"))
        // Each calculation waits for the other to start. One at a time, the first
        // gives up waiting and records that it ran alone.
        val bothStarted = CountDownLatch(portfolios.size)
        val ranAlone = Collections.synchronizedList(mutableListOf<String>())
        whenever(trnService.query(any<Portfolio>(), any())).thenAnswer { invocation ->
            val portfolio = invocation.getArgument<Portfolio>(0)
            bothStarted.countDown()
            if (!bothStarted.await(2, TimeUnit.SECONDS)) ranAlone.add(portfolio.code)
            TrnResponse()
        }

        service.aggregate(portfolios, 12, USD)

        assertThat(ranAlone).isEmpty()
    }

    @Test
    fun `should nudge price history once for every portfolio's assets`() {
        val apple = asset("AAPL")
        val msft = asset("MSFT")
        whenever(trnService.query(any<Portfolio>(), any())).thenAnswer { invocation ->
            val portfolio = invocation.getArgument<Portfolio>(0)
            val asset = if (portfolio.code == "P1") apple else msft
            TrnResponse(listOf(buy(portfolio, asset)))
        }
        whenever(priceService.getBulkPrices(any(), any())).thenReturn(BulkPriceResponse(emptyMap()))
        whenever(priceService.ensureHistory(any(), any())).thenReturn(EnsureHistoryResponse(scheduled = 2))

        service.aggregate(listOf(portfolio("P1"), portfolio("P2")), 12, USD)

        val requests = argumentCaptor<EnsureHistoryRequest>()
        verify(priceService, times(1)).ensureHistory(requests.capture(), any())
        assertThat(requests.firstValue.assetIds).containsExactlyInAnyOrder(apple.id, msft.id)
    }

    private fun portfolio(code: String) =
        Portfolio(id = code, code = code, name = code, currency = USD, base = USD, owner = owner)

    private fun asset(code: String) = Asset(code = code, id = "$code-id", market = Market("US"))

    private fun buy(
        portfolio: Portfolio,
        asset: Asset
    ) = Trn(
        trnType = TrnType.BUY,
        asset = asset,
        tradeDate = dateUtils.date.minusMonths(3),
        quantity = BigDecimal.TEN,
        price = BigDecimal.TEN,
        tradeAmount = BigDecimal("100"),
        tradeCurrency = USD,
        portfolio = portfolio
    )
}