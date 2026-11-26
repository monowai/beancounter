package com.beancounter.marketdata.trn.cash

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.TRNS_ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * An auto-settle cash leg is edited through the trade it settles, so the view
 * asks for that trade by the leg's id.
 */
@SpringMvcDbTest
class SettledTradeLookupTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @MockitoBean
    private lateinit var cacheInvalidationProducer: CacheInvalidationProducer

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var portfolioShareRepository: PortfolioShareRepository

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var token: Jwt
    private lateinit var testUser: SystemUser
    private lateinit var usdCashAsset: Asset
    private lateinit var aapl: Asset
    private lateinit var buy: Trn

    @BeforeEach
    fun configure() {
        testUser = SystemUser()
        token = mockAuthConfig.login(testUser, systemUserService)
        val bcMvcHelper = BcMvcHelper(mockMvc, token)
        Mockito
            .`when`(fxClientService.getRates(any(), any()))
            .thenReturn(FxResponse(FxPairResults()))

        val cashInput = AssetUtils.getCash(Constants.USD.code)
        usdCashAsset =
            assetService.handle(AssetRequest(mapOf(Constants.USD.code to cashInput))).data[Constants.USD.code]!!
        aapl =
            assetService.handle(AssetRequest(AssetInput(Constants.NASDAQ.code, "AAPL"), "AAPL")).data["AAPL"]!!

        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        val master = bcMvcHelper.portfolio(PortfolioInput(code = "MASTER_$uniqueId", base = "USD", currency = "USD"))
        val invest =
            bcMvcHelper.portfolio(
                PortfolioInput(code = "INV_$uniqueId", base = "USD", currency = "USD", cashPortfolioId = master.id)
            )
        mockAuthConfig.login(testUser, systemUserService)
        buy = buyInto(invest)
    }

    @Test
    fun `should return the trade an auto-settle leg settles`() {
        val legs = legsOf(buy)
        assertThat(legs).hasSize(2)

        legs.forEach { leg ->
            val body =
                mockMvc
                    .perform(get("$TRNS_ROOT/{trnId}/settles", leg.id).with(jwt().jwt(token)))
                    .andExpect(status().isOk)
                    .andReturn()
                    .response.contentAsString
            val trns = objectMapper.readValue(body, TrnResponse::class.java).data.trns
            assertThat(trns.map { it.id }).containsExactly(buy.id)
        }
    }

    @Test
    fun `should return 404 for a trade that settles nothing`() {
        mockMvc
            .perform(get("$TRNS_ROOT/{trnId}/settles", buy.id).with(jwt().jwt(token)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should return 404 for another user's leg`() {
        val leg = legsOf(buy).first()
        val otherToken = mockAuthConfig.login(SystemUser(), systemUserService)

        mockMvc
            .perform(get("$TRNS_ROOT/{trnId}/settles", leg.id).with(jwt().jwt(otherToken)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should return the trade when the leg sits in another owner's shared funding portfolio`() {
        val fundingOwner = SystemUser()
        val fundingToken = mockAuthConfig.login(fundingOwner, systemUserService)
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        val funding =
            BcMvcHelper(mockMvc, fundingToken)
                .portfolio(PortfolioInput(code = "FUND_$uniqueId", base = "USD", currency = "USD"))
        mockAuthConfig.login(testUser, systemUserService)
        val invest =
            BcMvcHelper(mockMvc, token).portfolio(
                PortfolioInput(code = "INVX_$uniqueId", base = "USD", currency = "USD", cashPortfolioId = funding.id)
            )
        portfolioShareRepository.save(
            PortfolioShare(
                portfolio = funding,
                sharedWith = invest.owner,
                createdBy = funding.owner,
                status = ShareStatus.ACTIVE
            )
        )
        mockAuthConfig.login(testUser, systemUserService)
        val trade = buyInto(invest)
        val fundingLeg = legsOf(trade).single { it.portfolio.id == funding.id }

        val body =
            mockMvc
                .perform(get("$TRNS_ROOT/{trnId}/settles", fundingLeg.id).with(jwt().jwt(token)))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val trns = objectMapper.readValue(body, TrnResponse::class.java).data.trns
        assertThat(trns.map { it.id }).containsExactly(trade.id)
    }

    private fun buyInto(portfolio: Portfolio): Trn =
        trnService
            .save(
                portfolio,
                TrnRequest(
                    portfolio.id,
                    listOf(
                        TrnInput(
                            assetId = aapl.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.BUY,
                            quantity = BigDecimal("10"),
                            price = BigDecimal("200"),
                            tradeAmount = BigDecimal("2000"),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            cashAmount = BigDecimal("-2000"),
                            tradeCashRate = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = TrnStatus.SETTLED
                        )
                    )
                )
            ).single()

    private fun legsOf(parent: Trn): List<Trn> =
        trnRepository.findByCallerRefProviderAndCallerRefBatch("BC-AUTO", parent.callerRef!!.callerId)
}