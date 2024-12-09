package com.contracts.data

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoWebAuth
import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.OffMarketEnricher
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.EcbService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.BcRowAdapter
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.trn.cash.CashBalancesBean
import io.restassured.RestAssured
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

/**
 * Verifies assumptions around Real Estate oriented transactions.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class RealestateBase {
    private lateinit var jwt: JwtAuthenticationToken

    @LocalServerPort
    lateinit var port: String

    @MockBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockBean
    internal lateinit var jwtDecoder: JwtDecoder

    @Autowired
    internal lateinit var tokenService: TokenService

    @Autowired
    lateinit var noWebAuth: NoWebAuth

    @MockBean
    internal lateinit var cashBalancesBean: CashBalancesBean

    @MockBean
    internal lateinit var bcRowAdapter: BcRowAdapter

    @MockBean
    internal lateinit var ecbService: EcbService

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var systemUserService: SystemUserService

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    internal lateinit var authUtilService: AuthUtilService

    lateinit var portfolio: Portfolio

    val tenK = BigDecimal("10000.00")
    private final val oneK = BigDecimal("1000")
    val nOneK: BigDecimal = oneK.negate()
    private val tradeDate = "2023-05-01"
    private val assetCode = "USD.RE"
    private val pTradeAmount = "tradeAmount"
    private val pCashAmount = "cashAmount"

    private val pQuantity = "quantity"

    private val batch = "20230501"
    private lateinit var houseAsset: Asset
    private val systemUser: SystemUser = ContractHelper.getSystemUser()

    @BeforeEach
    fun mockTrn() {
        RestAssured.port = Integer.valueOf(port)
        jwt =
            authUtilService.authenticate(
                systemUser
            )
        // Hardcode the id of the system user so we can find it in the off-market asset code
        Mockito.`when`(systemUserService.getOrThrow).thenReturn(systemUser)
        Mockito.`when`(systemUserService.getActiveUser()).thenReturn(systemUser)
        Mockito.`when`(systemUserService.find(any())).thenReturn(systemUser)
        // this needs to be in the DB as we persist portfolios
        systemUserRepository.save(systemUser)
        portfolio = portfolio()
        mortgage()
        reTrnFlow()
    }

    fun mortgage() {
        val m1 = "Mortgage 1"
        val m1Balance = trnCashResponse(m1)
        val m2 = "Mortgage 2"
        val m2Balance = trnCashResponse(m2)
        houseAsset = asset()
        assertThat(houseAsset)
            .isNotNull
            .extracting(
                "id",
                "code"
            ).containsExactly(
                assetCode,
                OffMarketEnricher.parseCode(
                    systemUser = systemUser,
                    assetCode
                )
            )

        assertThat(m1Balance).isNotNull.hasSize(1)
        assertThat(m2Balance).isNotNull.hasSize(1)
        assertThat(m1Balance.iterator().next())
            .extracting(
                "id",
                pTradeAmount,
                pCashAmount
            ).containsExactly(
                m1,
                tenK.negate(),
                BigDecimal.ZERO
            )
    }

    private fun trnCashResponse(trnId: String): Collection<Trn> {
        val assetInput =
            AssetInput.toCash(
                NZD,
                trnId
            )
        Mockito.`when`(keyGenUtils.id).thenReturn(trnId)
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            Pair(
                                assetInput.code,
                                assetInput
                            )
                        )
                    )
                ).data[assetInput.code]
        Mockito.`when`(keyGenUtils.id).thenReturn(trnId)
        return save(
            portfolio,
            TrnInput(
                callerRef =
                    CallerRef(
                        batch = batch,
                        callerId = keyGenUtils.id
                    ),
                tradeDate = DateUtils().getDate(tradeDate),
                assetId = asset!!.id,
                trnType = TrnType.BALANCE,
                tradeAmount = tenK.negate(),
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE
            )
        )
    }

    fun reTrnFlow() {
        Mockito.`when`(keyGenUtils.id).thenReturn("1")
        val buy =
            save(
                portfolio,
                TrnInput(
                    callerRef =
                        CallerRef(
                            batch = batch,
                            callerId = keyGenUtils.id
                        ),
                    tradeDate = DateUtils().getDate(tradeDate),
                    assetId = houseAsset.id,
                    trnType = TrnType.BALANCE,
                    tradeAmount = tenK,
                    tradeBaseRate = BigDecimal.ONE,
                    tradeCashRate = BigDecimal.ONE,
                    tradePortfolioRate = BigDecimal.ONE
                )
            )
        assertThat(buy).isNotNull.hasSize(1)
        // Source output for `re-response` contract tests. Need to replace the ID with RE-TEST
        assertThat(buy.iterator().next())
            .extracting(
                "id",
                pTradeAmount,
                pCashAmount
            ).containsExactly(
                "1",
                tenK,
                BigDecimal.ZERO
            )

        Mockito.`when`(keyGenUtils.id).thenReturn("2")
        val r =
            save(
                portfolio,
                TrnInput(
                    callerRef =
                        CallerRef(
                            batch = batch,
                            callerId = keyGenUtils.id
                        ),
                    tradeDate = DateUtils().getDate(tradeDate),
                    assetId = houseAsset.id,
                    trnType = TrnType.BALANCE,
                    tradeAmount = nOneK,
                    tradeBaseRate = BigDecimal.ONE,
                    tradeCashRate = BigDecimal.ONE,
                    tradePortfolioRate = BigDecimal.ONE
                )
            )
        assertThat(r).isNotNull.hasSize(1)
        verifyTrn(
            r,
            nOneK,
            nOneK
        )

        Mockito.`when`(keyGenUtils.id).thenReturn("3")
        val i =
            save(
                portfolio,
                TrnInput(
                    callerRef =
                        CallerRef(
                            batch = batch,
                            callerId = keyGenUtils.id
                        ),
                    assetId = houseAsset.id,
                    tradeDate = DateUtils().getDate(tradeDate),
                    trnType = TrnType.BALANCE,
                    tradeAmount = oneK,
                    tradeBaseRate = BigDecimal.ONE,
                    tradeCashRate = BigDecimal.ONE,
                    tradePortfolioRate = BigDecimal.ONE
                )
            )
        assertThat(i).isNotNull.hasSize(1)
        verifyTrn(
            i,
            oneK,
            oneK
        )
    }

    private fun verifyTrn(
        r: Collection<Trn>,
        tradeAmount: BigDecimal,
        quantity: BigDecimal
    ) {
        assertThat(r.iterator().next())
            .extracting(
                pTradeAmount,
                pQuantity,
                pCashAmount
            ).containsExactly(
                tradeAmount,
                quantity,
                BigDecimal.ZERO
            )
    }

    private fun asset(): Asset {
        val assetInput =
            AssetInput.toRealEstate(
                Constants.USD,
                assetCode,
                "NY Apartment",
                "test-user"
            )
        Mockito.`when`(keyGenUtils.id).thenReturn(assetCode)
        return assetService
            .handle(
                AssetRequest(
                    mapOf(
                        Pair(
                            assetInput.code,
                            assetInput
                        )
                    )
                )
            ).data[assetInput.code]!!
    }

    private fun portfolio(code: String = "RE-TEST"): Portfolio {
        Mockito.`when`(keyGenUtils.id).thenReturn(code)
        return portfolioService.save(listOf(PortfolioInput(code))).iterator().next()
    }

    private fun save(
        portfolio: Portfolio,
        trnInput: TrnInput
    ): Collection<Trn> =
        trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                arrayOf(trnInput)
            )
        )
}