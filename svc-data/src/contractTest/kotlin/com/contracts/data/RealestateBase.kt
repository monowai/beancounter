package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.BcRowAdapter
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.trn.cash.CashBalancesBean
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

private const val assetCode = "USD.RE"
private const val pTradeAmount = "tradeAmount"
private const val pCashAmount = "cashAmount"

private const val pQuantity = "quantity"

/**
 * Verifies assumptions around Real Estate oriented transactions.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@WebAppConfiguration
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class RealestateBase {

    @Autowired
    lateinit var authConfig: NoAuthConfig

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var keyGenUtils: KeyGenUtils

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    internal lateinit var context: WebApplicationContext

    @Autowired
    internal lateinit var systemUserService: SystemUserService

    @MockBean
    internal lateinit var bcRowAdapter: BcRowAdapter

    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean

    val tenK = BigDecimal("10000.00")
    private final val oneK = BigDecimal("1000")
    val nOneK = BigDecimal.ZERO - oneK
    private val tradeDate = "2023-05-01"

    @BeforeEach
    fun mockTrn() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        ContractHelper.authUser(
            systemUserService.save(ContractHelper.getSystemUser()),
            authConfig,
        )
        reTrnFlow()
    }

    fun reTrnFlow() {
        val portfolio = portfolio()

        val houseAsset = asset(name = "NY Apartment")
        assertThat(houseAsset)
            .isNotNull
            .extracting("id", "code")
            .containsExactly(assetCode, assetCode)

        Mockito.`when`(keyGenUtils.id).thenReturn("1")
        val buy = save(
            portfolio,
            TrnInput(
                callerRef = CallerRef(batch = "20230501", callerId = keyGenUtils.id),
                tradeDate = DateUtils().getLocalDate(tradeDate),
                assetId = houseAsset!!.id,
                trnType = TrnType.BUY,
                tradeAmount = tenK,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
            ),
        )
        assertThat(buy.data).isNotNull.hasSize(1)
        // Source output for `re-response` contract tests. Need to replace the ID with RE-TEST
        assertThat(buy.data.iterator().next())
            .extracting("id", pTradeAmount, pCashAmount)
            .containsExactly("1", tenK, BigDecimal.ZERO.minus(tenK))

        Mockito.`when`(keyGenUtils.id).thenReturn("2")
        val r = save(
            portfolio,
            TrnInput(
                callerRef = CallerRef(batch = "20230501", callerId = keyGenUtils.id),
                tradeDate = DateUtils().getLocalDate(tradeDate),
                assetId = houseAsset.id,
                trnType = TrnType.REDUCE,
                tradeAmount = oneK,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
            ),
        )
        assertThat(r.data).isNotNull.hasSize(1)
        verifyTrn(r, nOneK, nOneK)

        Mockito.`when`(keyGenUtils.id).thenReturn("3")
        val i = save(
            portfolio,
            TrnInput(
                callerRef = CallerRef(batch = "20230501", callerId = keyGenUtils.id),
                assetId = houseAsset.id,
                tradeDate = DateUtils().getLocalDate(tradeDate),
                trnType = TrnType.INCREASE,
                tradeAmount = oneK,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
            ),
        )
        assertThat(i.data).isNotNull.hasSize(1)
        verifyTrn(i, oneK, oneK)
    }

    private fun verifyTrn(r: TrnResponse, tradeAmount: BigDecimal, quantity: BigDecimal) {
        assertThat(r.data.iterator().next())
            .extracting(pTradeAmount, pQuantity, pCashAmount)
            .containsExactly(tradeAmount, quantity, BigDecimal.ZERO)
    }

    private fun asset(currency: Currency = Constants.USD, name: String): Asset? {
        val house = AssetInput.toRealEstate(currency, name)
        Mockito.`when`(keyGenUtils.id).thenReturn(assetCode)
        return assetService.handle(
            AssetRequest(
                mapOf(Pair(house.code, house)),
            ),
        ).data[house.code]
    }

    private fun portfolio(code: String = "RE-TEST"): Portfolio {
        Mockito.`when`(keyGenUtils.id).thenReturn(code)
        return portfolioService.save(listOf(PortfolioInput(code))).iterator().next()
    }

    private fun save(
        portfolio: Portfolio,
        trnInput: TrnInput,
    ): TrnResponse {
        return trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                arrayOf(trnInput),
            ),
        )
    }
}
