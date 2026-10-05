package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.Constants.Companion.usdCashBalance
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.CashAccumulator
import com.beancounter.position.accumulation.DepositBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.accumulation.WithdrawalBehaviour
import com.beancounter.position.cache.NoOpPerformanceCacheService
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.utils.CurrencyResolver
import org.assertj.core.api.Assertions.assertThat
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

/**
 * External cash flows in TWR. A DEPOSIT or WITHDRAWAL changes the cash balance by its
 * quantity, so the flow must be that same quantity: anything else leaves balance and
 * contributions out of step and reads as return. Runs a real [Accumulator].
 */
@ExtendWith(MockitoExtension::class)
class PerformanceCashFlowTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    private lateinit var performanceService: PerformanceService

    private val dateUtils = DateUtils()
    private val today = dateUtils.date
    private val portfolio =
        Portfolio(
            id = "cash-flow-pf",
            code = "CASHFLOW",
            name = "Cash flow",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setup() {
        whenever(tokenService.bearerToken).thenReturn("test-token")
        val cashAccumulator = CashAccumulator(CurrencyResolver())
        performanceService =
            PerformanceService(
                trnService = trnService,
                accumulator =
                    Accumulator(
                        TrnBehaviourFactory(
                            listOf(DepositBehaviour(cashAccumulator), WithdrawalBehaviour(cashAccumulator))
                        )
                    ),
                priceService = priceService,
                fxRateService = fxRateService,
                twrCalculator = TwrCalculator(),
                irrCalculator = IrrCalculator(minHoldingDays = 365, dateUtils = dateUtils),
                dateUtils = dateUtils,
                tokenService = tokenService,
                cacheService = NoOpPerformanceCacheService()
            )
        lenient().`when`(fxRateService.getBulkRates(any(), any())).thenReturn(BulkFxResponse(emptyMap()))
    }

    @Test
    fun `should take a deposit flow from the quantity the balance is credited with`() {
        givenTrns(cash(TrnType.DEPOSIT, today.minusMonths(3), quantity = "1000", cashAmount = "800"))

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("1000")
        assertThat(last.netContributions).isEqualByComparingTo("1000")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
    }

    @Test
    fun `should take a withdrawal flow from the quantity the balance is debited with`() {
        givenTrns(
            cash(TrnType.DEPOSIT, today.minusMonths(6), quantity = "1000", cashAmount = "1000"),
            cash(TrnType.WITHDRAWAL, today.minusMonths(3), quantity = "-400", cashAmount = "-250")
        )

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("600")
        assertThat(last.netContributions).isEqualByComparingTo("600")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
    }

    private fun givenTrns(vararg trns: Trn) {
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(trns.sortedBy { it.tradeDate }))
    }

    private fun cash(
        trnType: TrnType,
        date: LocalDate,
        quantity: String,
        cashAmount: String
    ) = Trn(
        trnType = trnType,
        asset = usdCashBalance,
        tradeDate = date,
        quantity = BigDecimal(quantity),
        tradeAmount = BigDecimal(quantity).abs(),
        cashAsset = usdCashBalance,
        cashAmount = BigDecimal(cashAmount),
        cashCurrency = USD,
        tradeCurrency = USD,
        portfolio = portfolio
    )
}