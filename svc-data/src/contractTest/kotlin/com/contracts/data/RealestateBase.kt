package com.contracts.data

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
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
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.OffMarketEnricher
import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.fx.fxrates.EcbService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.BcRowAdapter
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import io.restassured.RestAssured
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
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

    @MockitoBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockitoBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    internal lateinit var cashService: CashService

    @MockitoBean
    internal lateinit var bcRowAdapter: BcRowAdapter

    @MockitoBean
    internal lateinit var ecbService: EcbService

    @MockitoBean
    private lateinit var trnService: TrnService

    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var assetFinder: AssetFinder

    @MockitoBean
    private lateinit var systemUserService: SystemUserService

    @MockitoBean
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    internal lateinit var authUtilService: AuthUtilService

    lateinit var portfolio: Portfolio

    val tenK = BigDecimal("10000.00")
    private val oneK = BigDecimal("1000")
    val nOneK: BigDecimal = oneK.negate()
    private val tradeDate = "2023-05-01"
    private val assetCode = "USD.RE"
    private val name = "NY Apartment"
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
        Mockito.`when`(systemUserService.getOrThrow()).thenReturn(systemUser)
        Mockito.`when`(systemUserService.getActiveUser()).thenReturn(systemUser)
        Mockito.`when`(systemUserService.find(any())).thenReturn(systemUser)
        // this needs to be in the DB as we persist portfolios
        systemUserRepository.save(systemUser)

        // Mock asset service for cash assets
        mockCashAssets()

        portfolio = portfolio()
        mortgage()
        reTrnFlow()
    }

    private fun mockCashAssets() {
        // Mock cash assets for mortgage transactions
        val mortgage1Asset = createCashAsset("Mortgage 1", "CASH")
        val mortgage2Asset = createCashAsset("Mortgage 2", "CASH")
        val houseAsset = createRealEstateAsset()

        // Mock asset service responses for specific requests
        Mockito.`when`(assetService.handle(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<AssetRequest>(0)
            val assets = mutableMapOf<String, Asset>()

            request.data.forEach { (key, assetInput) ->
                val asset =
                    when {
                        assetInput.code == "Mortgage 1" -> mortgage1Asset
                        assetInput.code == "Mortgage 2" -> mortgage2Asset
                        assetInput.code == assetCode -> houseAsset
                        else -> createCashAsset(assetInput.code, assetInput.market)
                    }
                assets[key] = asset
            }

            AssetUpdateResponse(assets)
        }

        // Mock find method
        Mockito.`when`(assetService.find(any())).thenAnswer { invocation ->
            when (val assetId = invocation.getArgument<String>(0)) {
                "Mortgage 1" -> mortgage1Asset
                "Mortgage 2" -> mortgage2Asset
                assetCode -> houseAsset
                else -> createCashAsset(assetId, "CASH")
            }
        }

        // Mock assetFinder.find method
        Mockito.`when`(assetFinder.find(any())).thenAnswer { invocation ->
            when (val assetId = invocation.getArgument<String>(0)) {
                "Mortgage 1" -> mortgage1Asset
                "Mortgage 2" -> mortgage2Asset
                assetCode -> houseAsset
                else -> createCashAsset(assetId, "CASH")
            }
        }

        // Mock TrnRepository to avoid database persistence
        Mockito
            .`when`(
                trnRepository.saveAll(
                    any<Collection<Trn>>()
                )
            ).thenAnswer { invocation ->
                val trns = invocation.getArgument<Collection<Trn>>(0)
                trns
            }

        // Mock TrnService.save method
        Mockito
            .`when`(
                trnService.save(
                    any<Portfolio>(),
                    any<TrnRequest>()
                )
            ).thenAnswer { invocation ->
                val portfolio = invocation.getArgument<Portfolio>(0)
                val trnRequest = invocation.getArgument<TrnRequest>(1)
                val trns = mutableListOf<Trn>()

                trnRequest.data.forEach { trnInput ->
                    val trn =
                        Trn(
                            id = keyGenUtils.id,
                            trnType = trnInput.trnType,
                            tradeDate = trnInput.tradeDate,
                            asset = createCashAsset(trnInput.assetId ?: "test", "CASH"),
                            quantity =
                                if (trnInput.quantity ==
                                    BigDecimal.ZERO
                                ) {
                                    trnInput.tradeAmount
                                } else {
                                    trnInput.quantity
                                },
                            callerRef =
                                CallerRef
                                    .from(trnInput.callerRef),
                            price = trnInput.price,
                            tradeAmount = trnInput.tradeAmount,
                            tradeCurrency =
                                com.beancounter.common.model
                                    .Currency("USD"),
                            cashAsset = null,
                            cashCurrency = null,
                            tradeCashRate = trnInput.tradeCashRate,
                            tradeBaseRate = trnInput.tradeBaseRate,
                            tradePortfolioRate = trnInput.tradePortfolioRate,
                            cashAmount = trnInput.cashAmount,
                            portfolio = portfolio,
                            settleDate = trnInput.settleDate,
                            fees = trnInput.fees,
                            tax = trnInput.tax,
                            comments = trnInput.comments,
                            status = trnInput.status
                        )
                    trns.add(trn)
                }

                trns
            }

        // Mock TrnService.findForPortfolio method for GET requests
        Mockito
            .`when`(
                trnService.findForPortfolio(
                    any<String>(),
                    any<java.time.LocalDate>()
                )
            ).thenAnswer { invocation ->
                val portfolioId = invocation.getArgument<String>(0)
                val date = invocation.getArgument<java.time.LocalDate>(1)

                if (portfolioId == "RE-TEST" && date.toString() == "2023-05-01") {
                    val mortgage1Asset = createMortgageAsset("Mortgage 1", "MORTGAGE 1", "Mortgage 1")
                    val mortgage2Asset = createMortgageAsset("Mortgage 2", "MORTGAGE 2", "Mortgage 2")
                    val realEstateAsset = createRealEstateAsset()

                    listOf(
                        Trn(
                            id = "Mortgage 1",
                            trnType = TrnType.BALANCE,
                            tradeDate = java.time.LocalDate.parse("2023-05-01"),
                            asset = mortgage1Asset,
                            quantity = BigDecimal("-10000.000000"),
                            callerRef =
                                CallerRef("BC", "20230501", "Mortgage 1"),
                            price = BigDecimal.ZERO,
                            tradeAmount = BigDecimal("-10000.00"),
                            tradeCurrency =
                                com.beancounter.common.model
                                    .Currency("USD"),
                            cashAsset = null,
                            cashCurrency = null,
                            tradeCashRate = BigDecimal.ONE,
                            tradeBaseRate = BigDecimal.ONE,
                            tradePortfolioRate = BigDecimal.ONE,
                            cashAmount = BigDecimal.ZERO,
                            portfolio = portfolio,
                            settleDate = null,
                            fees = BigDecimal.ZERO,
                            tax = BigDecimal.ZERO,
                            comments = null,
                            status = com.beancounter.common.model.TrnStatus.CONFIRMED
                        ),
                        Trn(
                            id = "Mortgage 2",
                            trnType = TrnType.BALANCE,
                            tradeDate = java.time.LocalDate.parse("2023-05-01"),
                            asset = mortgage2Asset,
                            quantity = BigDecimal("-10000.000000"),
                            callerRef =
                                CallerRef("BC", "20230501", "Mortgage 2"),
                            price = BigDecimal.ZERO,
                            tradeAmount = BigDecimal("-10000.00"),
                            tradeCurrency =
                                com.beancounter.common.model
                                    .Currency("USD"),
                            cashAsset = null,
                            cashCurrency = null,
                            tradeCashRate = BigDecimal.ONE,
                            tradeBaseRate = BigDecimal.ONE,
                            tradePortfolioRate = BigDecimal.ONE,
                            cashAmount = BigDecimal.ZERO,
                            portfolio = portfolio,
                            settleDate = null,
                            fees = BigDecimal.ZERO,
                            tax = BigDecimal.ZERO,
                            comments = null,
                            status = com.beancounter.common.model.TrnStatus.CONFIRMED
                        ),
                        Trn(
                            id = "1",
                            trnType = TrnType.BALANCE,
                            tradeDate = java.time.LocalDate.parse("2023-05-01"),
                            asset = realEstateAsset,
                            quantity = BigDecimal("10000.000000"),
                            callerRef =
                                CallerRef("BC", "20230501", "1"),
                            price = BigDecimal.ZERO,
                            tradeAmount = BigDecimal("10000.00"),
                            tradeCurrency =
                                com.beancounter.common.model
                                    .Currency("USD"),
                            cashAsset = null,
                            cashCurrency = null,
                            tradeCashRate = BigDecimal.ONE,
                            tradeBaseRate = BigDecimal.ONE,
                            tradePortfolioRate = BigDecimal.ONE,
                            cashAmount = BigDecimal.ZERO,
                            portfolio = portfolio,
                            settleDate = null,
                            fees = BigDecimal.ZERO,
                            tax = BigDecimal.ZERO,
                            comments = null,
                            status = com.beancounter.common.model.TrnStatus.CONFIRMED
                        ),
                        Trn(
                            id = "2",
                            trnType = TrnType.BALANCE,
                            tradeDate = java.time.LocalDate.parse("2023-05-01"),
                            asset = realEstateAsset,
                            quantity = BigDecimal("-1000.000000"),
                            callerRef =
                                CallerRef("BC", "20230501", "2"),
                            price = BigDecimal.ZERO,
                            tradeAmount = BigDecimal("-1000.00"),
                            tradeCurrency =
                                com.beancounter.common.model
                                    .Currency("USD"),
                            cashAsset = null,
                            cashCurrency = null,
                            tradeCashRate = BigDecimal.ONE,
                            tradeBaseRate = BigDecimal.ONE,
                            tradePortfolioRate = BigDecimal.ONE,
                            cashAmount = BigDecimal.ZERO,
                            portfolio = portfolio,
                            settleDate = null,
                            fees = BigDecimal.ZERO,
                            tax = BigDecimal.ZERO,
                            comments = null,
                            status = com.beancounter.common.model.TrnStatus.CONFIRMED
                        ),
                        Trn(
                            id = "3",
                            trnType = TrnType.BALANCE,
                            tradeDate = java.time.LocalDate.parse("2023-05-01"),
                            asset = realEstateAsset,
                            quantity = BigDecimal("1000.000000"),
                            callerRef =
                                CallerRef("BC", "20230501", "3"),
                            price = BigDecimal.ZERO,
                            tradeAmount = BigDecimal("1000.00"),
                            tradeCurrency =
                                com.beancounter.common.model
                                    .Currency("USD"),
                            cashAsset = null,
                            cashCurrency = null,
                            tradeCashRate = BigDecimal.ONE,
                            tradeBaseRate = BigDecimal.ONE,
                            tradePortfolioRate = BigDecimal.ONE,
                            cashAmount = BigDecimal.ZERO,
                            portfolio = portfolio,
                            settleDate = null,
                            fees = BigDecimal.ZERO,
                            tax = BigDecimal.ZERO,
                            comments = null,
                            status = com.beancounter.common.model.TrnStatus.CONFIRMED
                        )
                    )
                } else {
                    emptyList()
                }
            }
    }

    private fun createCashAsset(
        code: String,
        market: String
    ): Asset =
        Asset(
            code = code,
            id = code,
            name = "$code Balance",
            market =
                com.beancounter.common.model.Market(
                    code = market,
                    currencyId = "USD",
                    timezoneId = "UTC",
                    timezone = java.util.TimeZone.getTimeZone("UTC"),
                    priceTime = java.time.LocalTime.of(19, 0),
                    daysToSubtract = 1,
                    enricher = "ECHO",
                    multiplier = BigDecimal.ONE
                ),
            assetCategory =
                com.beancounter.common.model
                    .AssetCategory("CASH", "Cash"),
            priceSymbol = code,
            version = "1",
            status = com.beancounter.common.model.Status.Active
        )

    private fun createRealEstateAsset(): Asset {
        val formattedCode =
            if (assetCode.startsWith(systemUser.id)) {
                assetCode
            } else {
                "${systemUser.id}.${assetCode.uppercase()}"
            }
        return Asset(
            code = formattedCode,
            id = assetCode,
            name = name,
            market =
                com.beancounter.common.model.Market(
                    code = "OFFM",
                    currencyId = "USD",
                    timezoneId = "UTC",
                    timezone = java.util.TimeZone.getTimeZone("UTC"),
                    priceTime = java.time.LocalTime.of(19, 0),
                    daysToSubtract = 1,
                    enricher = "DEFAULT",
                    multiplier = BigDecimal.ONE
                ),
            assetCategory =
                com.beancounter.common.model
                    .AssetCategory("RE", "Real Estate"),
            priceSymbol = "USD",
            version = "1",
            status = com.beancounter.common.model.Status.Active,
            systemUser = systemUser
        )
    }

    private fun createMortgageAsset(
        id: String,
        code: String,
        name: String
    ): Asset =
        Asset(
            code = code,
            id = id,
            name = name,
            market =
                com.beancounter.common.model.Market(
                    code = "CASH",
                    currencyId = "USD",
                    timezoneId = "UTC",
                    timezone = java.util.TimeZone.getTimeZone("UTC"),
                    priceTime = java.time.LocalTime.of(19, 0),
                    daysToSubtract = 1,
                    enricher = "DEFAULT",
                    multiplier = BigDecimal.ONE
                ),
            assetCategory =
                com.beancounter.common.model
                    .AssetCategory("CASH", "Cash"),
            priceSymbol = "NZD",
            version = "1",
            status = com.beancounter.common.model.Status.Active
        )

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
                                "${assetInput.code}:${assetInput.market}",
                                assetInput
                            )
                        )
                    )
                ).data["${assetInput.code}:${assetInput.market}"]
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