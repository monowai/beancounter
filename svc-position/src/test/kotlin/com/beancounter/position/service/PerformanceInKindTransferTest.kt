package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.Constants.Companion.usdCashBalance
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.CashAccumulator
import com.beancounter.position.accumulation.DepositBehaviour
import com.beancounter.position.accumulation.ReduceBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.accumulation.WithdrawalBehaviour
import com.beancounter.position.cache.NoOpPerformanceCacheService
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.irr.TwrCalculator
import com.beancounter.position.utils.CurrencyResolver
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

/**
 * ADD and REDUCE move units into or out of a portfolio without cash (in-specie
 * transfers). TWR treats each as an external flow at the units' market value on the
 * trade date, so moving units between portfolios is neither return nor loss. Runs a
 * real [Accumulator] so quantities come from actual position state.
 *
 * A BUY or SELL with no cash asset settles outside the portfolio: the money never
 * passes through its cash, so the trade amount is an external flow too.
 */
@ExtendWith(MockitoExtension::class)
class PerformanceInKindTransferTest {
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
    private val fund = Asset(code = "FUND", id = "FUND-id", name = "Fund", market = Market("US", USD.code))
    private val portfolio =
        Portfolio(
            id = "in-kind-pf",
            code = "INKIND",
            name = "In kind",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setup() {
        whenever(tokenService.bearerToken).thenReturn("test-token")
        val currencyResolver = CurrencyResolver()
        val cashAccumulator = CashAccumulator(currencyResolver)
        val accumulator =
            Accumulator(
                TrnBehaviourFactory(
                    listOf(
                        BuyBehaviour(currencyResolver),
                        ReduceBehaviour(currencyResolver),
                        SellBehaviour(currencyResolver),
                        DepositBehaviour(cashAccumulator),
                        WithdrawalBehaviour(cashAccumulator)
                    )
                )
            )
        performanceService =
            PerformanceService(
                trnService = trnService,
                accumulator = accumulator,
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
    fun `should treat units added in specie as money in at market value`() {
        // Carried over at their original cost of 5; worth 20 on the day they arrive.
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            inKind(TrnType.ADD, today.minusMonths(3), quantity = "10", price = "5")
        )
        givenPrices { "20" }

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("1200")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(last.netContributions).isEqualByComparingTo("1200")
    }

    @Test
    fun `should treat units reduced in specie as money out at market value`() {
        givenTrns(
            inKind(TrnType.ADD, today.minusMonths(14), quantity = "10", price = "20"),
            deposit(today.minusMonths(6), "1000"),
            inKind(TrnType.REDUCE, today.minusMonths(2), quantity = "10", price = "0")
        )
        givenPrices { "20" }

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("1000")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(last.netContributions).isEqualByComparingTo("1000")
    }

    @Test
    fun `should count price movement after an in-specie add as return`() {
        val arrived = today.minusMonths(3)
        givenTrns(inKind(TrnType.ADD, arrived, quantity = "10", price = "5"))
        givenPrices { date -> if (date.isAfter(arrived)) "30" else "20" }

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("300")
        assertThat(last.cumulativeReturn.toDouble()).isCloseTo(0.5, Offset.offset(0.0001))
        assertThat(last.netContributions).isEqualByComparingTo("200")
    }

    @Test
    fun `should treat a buy settled outside the portfolio as money in`() {
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            trade(TrnType.BUY, today.minusMonths(3), quantity = "10", price = "20")
        )
        givenPrices { "20" }

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("1200")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(last.netContributions).isEqualByComparingTo("1200")
    }

    @Test
    fun `should keep the realised gain when sale proceeds settle outside the portfolio`() {
        val sold = today.minusMonths(2)
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            trade(TrnType.BUY, today.minusMonths(5), quantity = "10", price = "20", settledInPortfolio = true),
            // Bought at 20, sold at 30: a 100 gain on 1000. The 300 proceeds leave the portfolio.
            trade(TrnType.SELL, sold, quantity = "10", price = "30")
        )
        givenPrices { date -> if (date.isBefore(sold)) "20" else "30" }

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        assertThat(last.marketValue).isEqualByComparingTo("800")
        assertThat(last.cumulativeReturn.toDouble()).isCloseTo(0.1, Offset.offset(0.0001))
        assertThat(last.netContributions).isEqualByComparingTo("700")
    }

    private fun givenTrns(vararg trns: Trn) {
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(trns.sortedBy { it.tradeDate }))
    }

    private fun givenPrices(closeOn: (LocalDate) -> String) {
        lenient().`when`(priceService.getBulkPrices(any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<BulkPriceRequest>(0)
            BulkPriceResponse(
                request.dates.associateWith { date ->
                    val priceDate = LocalDate.parse(date)
                    listOf(MarketData(asset = fund, priceDate = priceDate, close = BigDecimal(closeOn(priceDate))))
                }
            )
        }
    }

    private fun deposit(
        date: LocalDate,
        amount: String
    ) = Trn(
        trnType = TrnType.DEPOSIT,
        asset = usdCashBalance,
        tradeDate = date,
        quantity = BigDecimal(amount),
        tradeAmount = BigDecimal(amount),
        cashAmount = BigDecimal(amount),
        cashCurrency = USD,
        tradeCurrency = USD,
        portfolio = portfolio
    )

    private fun inKind(
        trnType: TrnType,
        date: LocalDate,
        quantity: String,
        price: String
    ) = Trn(
        trnType = trnType,
        asset = fund,
        tradeDate = date,
        quantity = BigDecimal(quantity),
        price = BigDecimal(price),
        tradeAmount = BigDecimal(quantity).multiply(BigDecimal(price)),
        tradeCurrency = USD,
        portfolio = portfolio
    )

    private fun trade(
        trnType: TrnType,
        date: LocalDate,
        quantity: String,
        price: String,
        settledInPortfolio: Boolean = false
    ): Trn {
        val amount = BigDecimal(quantity).multiply(BigDecimal(price))
        return Trn(
            trnType = trnType,
            asset = fund,
            tradeDate = date,
            quantity = BigDecimal(quantity),
            price = BigDecimal(price),
            tradeAmount = amount,
            cashAsset = if (settledInPortfolio) usdCashBalance else null,
            cashCurrency = USD,
            cashAmount = if (trnType == TrnType.BUY) amount.negate() else amount,
            tradeCurrency = USD,
            tradeCashRate = BigDecimal.ONE,
            portfolio = portfolio
        )
    }
}