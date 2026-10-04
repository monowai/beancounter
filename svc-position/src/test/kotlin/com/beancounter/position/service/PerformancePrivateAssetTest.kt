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
import com.beancounter.position.accumulation.BalanceBehaviour
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.CashAccumulator
import com.beancounter.position.accumulation.DepositBehaviour
import com.beancounter.position.accumulation.DividendBehaviour
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
 * PRIVATE-market assets in time-weighted return. Runs a real [Accumulator] so cash legs,
 * BALANCE cost basis and market values come from actual position state, not mocks.
 *
 * - PRIVATE assets are out of TWR unless [Asset.includeInPerformance] is set. Money moved
 *   into or out of an excluded asset crosses the TWR boundary, so it is an external flow.
 * - A BALANCE snapshot on an included asset is mostly fresh principal: the contribution
 *   part is an external flow and only the remainder is return.
 */
@ExtendWith(MockitoExtension::class)
class PerformancePrivateAssetTest {
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
    private val privateMarket = Market("PRIVATE", USD.code)
    private val portfolio =
        Portfolio(
            id = "private-pf",
            code = "PRIV",
            name = "Private",
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
                        DepositBehaviour(cashAccumulator),
                        WithdrawalBehaviour(cashAccumulator),
                        BalanceBehaviour(currencyResolver),
                        DividendBehaviour(currencyResolver)
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
    fun `should leave a private asset out of TWR by default`() {
        val unlisted = privateAsset("UNLISTED", category = "Equity")
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            buy(unlisted, today.minusMonths(3), "400")
        )
        // Revalued to double its cost: must not surface as return.
        givenPrices(unlisted to "800")

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("600")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(last.netContributions).isEqualByComparingTo("600")
    }

    @Test
    fun `should treat a dividend from an excluded asset as money in, not TWR dividends`() {
        val unlisted = privateAsset("UNLISTED", category = "Equity")
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            buy(unlisted, today.minusMonths(3), "400"),
            dividend(unlisted, today.minusMonths(1), "50")
        )

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("650")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(last.cumulativeDividends).isEqualByComparingTo("0")
    }

    @Test
    fun `should value an opted-in private asset at its latest price`() {
        val unlisted = privateAsset("UNLISTED", category = "Equity", included = true)
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            buy(unlisted, today.minusMonths(3), "400")
        )
        givenPrices(unlisted to "800")

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("1400")
        assertThat(last.cumulativeReturn.toDouble()).isCloseTo(0.4, Offset.offset(0.0001))
        assertThat(last.netContributions).isEqualByComparingTo("1000")
    }

    @Test
    fun `should leave an unflagged BALANCE pension out of TWR`() {
        val pension = privateAsset("PENSION", category = "POLICY")
        givenTrns(
            deposit(today.minusMonths(6), "1000"),
            balance(pension, today.minusMonths(14), "10000"),
            balance(pension, today.minusMonths(2), "11200", contribution = "1000")
        )
        givenPrices(pension to "1")

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("1000")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
    }

    @Test
    fun `should count only interest as return on an opted-in BALANCE pension`() {
        val pension = privateAsset("PENSION", category = "POLICY", included = true)
        givenTrns(
            balance(pension, today.minusMonths(14), "10000"),
            // 1000 contributed since the last snapshot; the other 200 is interest.
            balance(pension, today.minusMonths(2), "11200", contribution = "1000")
        )
        givenPrices(pension to "1")

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("11200")
        assertThat(last.cumulativeReturn.toDouble()).isCloseTo(0.02, Offset.offset(0.0001))
        assertThat(last.netContributions).isEqualByComparingTo("11000")
    }

    @Test
    fun `should show no return on an opted-in BALANCE pension without a contribution figure`() {
        val pension = privateAsset("PENSION", category = "POLICY", included = true)
        givenTrns(
            balance(pension, today.minusMonths(14), "10000"),
            balance(pension, today.minusMonths(2), "11200")
        )
        givenPrices(pension to "1")

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("11200")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
    }

    @Test
    fun `should keep a bank account in TWR and treat its BALANCE updates as flows`() {
        // ACCOUNT is cash-like: it stays in by default, and a restated balance is money
        // moved in or out, not return.
        val bank = privateAsset("BANK", category = "ACCOUNT")
        givenTrns(
            balance(bank, today.minusMonths(14), "5000"),
            balance(bank, today.minusMonths(2), "5600")
        )

        val result = performanceService.calculate(portfolio, 12)

        val last = result.data.series.last()
        assertThat(last.marketValue).isEqualByComparingTo("5600")
        assertThat(last.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(last.netContributions).isEqualByComparingTo("5600")
    }

    private fun privateAsset(
        code: String,
        category: String,
        included: Boolean = false
    ) = Asset(
        code = code,
        id = "$code-id",
        name = code,
        market = privateMarket,
        category = category,
        includeInPerformance = included
    )

    private fun givenTrns(vararg trns: Trn) {
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(trns.sortedBy { it.tradeDate }))
    }

    /** Every requested asset gets the same close on every requested date. */
    private fun givenPrices(vararg closes: Pair<Asset, String>) {
        lenient().`when`(priceService.getBulkPrices(any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<BulkPriceRequest>(0)
            BulkPriceResponse(
                request.dates.associateWith { date ->
                    closes.map { (asset, close) ->
                        MarketData(asset = asset, priceDate = LocalDate.parse(date), close = BigDecimal(close))
                    }
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

    private fun buy(
        asset: Asset,
        date: LocalDate,
        amount: String
    ) = Trn(
        trnType = TrnType.BUY,
        asset = asset,
        tradeDate = date,
        quantity = BigDecimal.ONE,
        price = BigDecimal(amount),
        tradeAmount = BigDecimal(amount),
        cashAsset = usdCashBalance,
        cashCurrency = USD,
        cashAmount = BigDecimal(amount).negate(),
        tradeCurrency = USD,
        tradeCashRate = BigDecimal.ONE,
        portfolio = portfolio
    )

    private fun dividend(
        asset: Asset,
        date: LocalDate,
        amount: String
    ) = Trn(
        trnType = TrnType.DIVI,
        asset = asset,
        tradeDate = date,
        quantity = BigDecimal.ZERO,
        tradeAmount = BigDecimal(amount),
        cashAsset = usdCashBalance,
        cashCurrency = USD,
        cashAmount = BigDecimal(amount),
        tradeCurrency = USD,
        tradeCashRate = BigDecimal.ONE,
        portfolio = portfolio
    )

    private fun balance(
        asset: Asset,
        date: LocalDate,
        amount: String,
        contribution: String? = null
    ) = Trn(
        trnType = TrnType.BALANCE,
        asset = asset,
        tradeDate = date,
        quantity = BigDecimal(amount),
        tradeAmount = BigDecimal(amount),
        tradeCurrency = USD,
        portfolio = portfolio
    ).apply { this.contribution = contribution?.let { BigDecimal(it) } }
}