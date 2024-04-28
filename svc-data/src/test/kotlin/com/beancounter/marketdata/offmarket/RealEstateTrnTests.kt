package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal

/**
 * Check the flow of a real estate purchase.
 */
@SpringMvcDbTest
class RealEstateTrnTests {
    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

    private lateinit var bcMvcHelper: BcMvcHelper

    @MockBean
    private lateinit var figiProxy: FigiProxy

    @MockBean
    private lateinit var fxClientService: FxRateService

    @MockBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory
    val tenK = BigDecimal("10000.00")
    private val pTradeAmount = "tradeAmount"
    private val pCashAmount = "cashAmount"
    private val pQuantity = "quantity"

    @Autowired
    fun setupObjects(
        mockMvc: MockMvc,
        mockAuthConfig: MockAuthConfig,
    ) {
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser()),
            )
        bcMvcHelper.registerUser()
        assertThat(figiProxy).isNotNull
        enrichmentFactory.register(DefaultEnricher())
        Mockito.`when`(fxClientService.getRates(any(), any()))
            .thenReturn(FxResponse(FxPairResults()))
    }

    @Test
    fun is_BuyHouse() {
        mockAuthConfig.login(SystemUser(), systemUserService)
        val house = AssetInput.toRealEstate(USD, "USAPT", "NY Apartment", "test-user")
        val houseAsset =
            assetService.handle(
                AssetRequest(
                    mapOf(Pair(house.code, house)),
                ),
            ).data[house.code]
        assertThat(houseAsset).isNotNull
        val portfolio = bcMvcHelper.portfolio(PortfolioInput("RE-TEST"))
        val purchase =
            TrnInput(
                callerRef = CallerRef(),
                assetId = houseAsset!!.id,
                trnType = TrnType.BUY,
                tradeAmount = tenK,
                tradeCashRate = BigDecimal.ONE,
            )

        val oneK = BigDecimal("1000")
        val reduce =
            TrnInput(
                callerRef = CallerRef(),
                assetId = houseAsset.id,
                trnType = TrnType.BALANCE,
                tradeAmount = oneK,
                tradeCashRate = BigDecimal.ONE,
            )

        val increase =
            TrnInput(
                callerRef = CallerRef(),
                assetId = houseAsset.id,
                trnType = TrnType.BALANCE,
                tradeAmount = oneK,
                tradeCashRate = BigDecimal.ONE,
            )

        val trns =
            trnService.save(
                portfolio,
                TrnRequest(
                    portfolio.id,
                    arrayOf(purchase, reduce, increase),
                ),
            )
        assertThat(trns.data).isNotNull.hasSize(3)
        // Source output for `re-response` contract tests. Need to replace the ID with RE-TEST
        val iterator = trns.data.iterator()
        val b = iterator.next()
        assertThat(b)
            .extracting(pTradeAmount, pCashAmount)
            .containsExactly(tenK, BigDecimal.ZERO.minus(tenK))

        val r = iterator.next()
        assertThat(r)
            .extracting(pTradeAmount, pQuantity, pCashAmount)
            .containsExactly(oneK, oneK, BigDecimal.ZERO)

        val i = iterator.next()
        assertThat(i)
            .extracting(pTradeAmount, pQuantity, pCashAmount)
            .containsExactly(oneK, oneK, BigDecimal.ZERO)
    }
}
