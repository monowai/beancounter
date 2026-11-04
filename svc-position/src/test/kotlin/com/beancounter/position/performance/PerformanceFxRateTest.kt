package com.beancounter.position.performance

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.CashAccumulator
import com.beancounter.position.accumulation.CurrencyResolver
import com.beancounter.position.accumulation.DepositBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.accumulation.WithdrawalBehaviour
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Every currency a performance pass holds is converted to portfolio currency at a
 * fetched rate. A missing rate fails the pass, so the pass must ask for the rate of
 * each cash currency as well as each trade currency.
 */
@ExtendWith(MockitoExtension::class)
class PerformanceFxRateTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    private lateinit var performanceService: PerformanceService

    private val today = DateUtils().date
    private val fund = Asset(code = "FUND", id = "FUND-id", name = "Fund", market = Market("US", currencyId = USD.code))
    private val nzdCash =
        Asset(
            code = "NZD BALANCE",
            id = "NZD-BALANCE-id",
            name = "NZD Balance",
            market = Market("CASH", currencyId = NZD.code),
            priceSymbol = NZD.code,
            category = "CASH"
        )
    private val portfolio =
        Portfolio(id = "fx-pf", code = "FXPF", name = "FX", currency = USD, base = USD, owner = owner)

    @BeforeEach
    fun setup() {
        whenever(tokenService.bearerToken).thenReturn("test-token")
        val currencyResolver = CurrencyResolver()
        val cashAccumulator = CashAccumulator(currencyResolver)
        performanceService =
            performanceService(
                trnService = trnService,
                accumulator =
                    Accumulator(
                        TrnBehaviourFactory(
                            listOf(
                                BuyBehaviour(currencyResolver),
                                DepositBehaviour(cashAccumulator),
                                WithdrawalBehaviour(cashAccumulator)
                            )
                        )
                    ),
                priceService = priceService,
                fxRateService = fxRateService,
                tokenService = tokenService
            )
    }

    @Test
    fun `should value cash in a currency no trade is in at the fetched rate`() {
        // A USD fund bought from NZD cash: 200 USD paid with 300 NZD.
        whenever(trnService.query(any<Portfolio>(), eq(DateUtils.TODAY)))
            .thenReturn(TrnResponse(listOf(buyFromNzdCash(today.minusMonths(3)))))
        whenever(priceService.getBulkPrices(any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<BulkPriceRequest>(0)
            BulkPriceResponse(
                request.dates.associateWith { date ->
                    listOf(MarketData(asset = fund, priceDate = LocalDate.parse(date), close = BigDecimal("20")))
                }
            )
        }
        // Answers only the pairs asked for, at 0.6 USD per NZD.
        whenever(fxRateService.getBulkRates(any(), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<BulkFxRequest>(0)
            BulkFxResponse(
                request.dates.associateWith {
                    FxPairResults(
                        request.pairs
                            .filter { pair -> pair == IsoCurrencyPair(NZD.code, USD.code) }
                            .associateWith { FxRate(from = NZD, to = USD, rate = BigDecimal("0.6")) }
                    )
                }
            )
        }

        val last =
            performanceService
                .calculate(portfolio, 12)
                .data.series
                .last()

        // 10 units at 20 USD, less 300 NZD at 0.6.
        assertThat(last.marketValue).isEqualByComparingTo("20")
    }

    private fun buyFromNzdCash(date: LocalDate) =
        Trn(
            trnType = TrnType.BUY,
            asset = fund,
            tradeDate = date,
            quantity = BigDecimal.TEN,
            price = BigDecimal("20"),
            tradeAmount = BigDecimal("200"),
            cashAsset = nzdCash,
            cashCurrency = NZD,
            cashAmount = BigDecimal("-300"),
            tradeCurrency = USD,
            tradeCashRate = BigDecimal("1.5"),
            portfolio = portfolio
        )
}