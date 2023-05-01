package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
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

/**
 * Base class for Trn Contract tests. This is called by the spring cloud contract verifier
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

    private lateinit var systemUser: SystemUser

    @Autowired
    internal lateinit var context: WebApplicationContext

    @Autowired
    internal lateinit var systemUserService: SystemUserService

    @MockBean
    internal lateinit var bcRowAdapter: BcRowAdapter

    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean

    val tenK = BigDecimal("10000.00")

    @BeforeEach
    fun mockTrn() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        systemUser = systemUserService.save(ContractHelper.getSystemUser())
        ContractHelper.authUser(systemUser, authConfig.jwtDecoder, authConfig.tokenService)

        Mockito.`when`(keyGenUtils.id).thenReturn("RE-TEST")
        val portfolio = portfolioService.save(listOf(PortfolioInput("RE-TEST"))).iterator().next()

        val house = AssetInput.toRealEstate(Constants.USD, "NY Apartment")
        Mockito.`when`(keyGenUtils.id).thenReturn(assetCode)
        val houseAsset = assetService.handle(
            AssetRequest(
                mapOf(Pair(house.code, house)),
            ),
        ).data[house.code]
        assertThat(houseAsset)
            .isNotNull
            .extracting("id", "code")
            .containsExactly(assetCode, assetCode)

        val oneK = BigDecimal("1000")
        val nOneK = BigDecimal.ZERO - oneK

        Mockito.`when`(keyGenUtils.id).thenReturn("1")
        val buy = save(
            portfolio,
            TrnInput(
                callerRef = CallerRef(callerId = "1"),
                tradeDate = DateUtils().getLocalDate("2023-05-01"),
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
            .extracting("id", "tradeAmount", "cashAmount")
            .containsExactly("1", tenK, BigDecimal.ZERO.minus(tenK))

        Mockito.`when`(keyGenUtils.id).thenReturn("2")
        val r = save(
            portfolio,
            TrnInput(
                callerRef = CallerRef(callerId = "2"),
                tradeDate = DateUtils().getLocalDate("2023-05-01"),
                assetId = houseAsset.id,
                trnType = TrnType.REDUCE,
                tradeAmount = oneK,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
            ),
        )
        assertThat(r.data).isNotNull.hasSize(1)
        assertThat(r.data.iterator().next())
            .extracting("tradeAmount", "quantity", "cashAmount")
            .containsExactly(nOneK, nOneK, BigDecimal.ZERO)

        Mockito.`when`(keyGenUtils.id).thenReturn("3")
        val i = save(
            portfolio,
            TrnInput(
                callerRef = CallerRef(callerId = "3"),
                assetId = houseAsset.id,
                tradeDate = DateUtils().getLocalDate("2023-05-01"),
                trnType = TrnType.INCREASE,
                tradeAmount = oneK,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
            ),
        )
        assertThat(i.data).isNotNull.hasSize(1)
        assertThat(i.data.iterator().next())
            .extracting("tradeAmount", "quantity", "cashAmount")
            .containsExactly(oneK, oneK, BigDecimal.ZERO)
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
