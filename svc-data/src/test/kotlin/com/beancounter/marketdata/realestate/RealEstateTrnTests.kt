package com.beancounter.marketdata.realestate

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
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

    @Autowired
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory
    val tenK = BigDecimal("10000.00")

    @BeforeEach
    fun setupObjects() {
        bcMvcHelper = BcMvcHelper(
            mockMvc,
            mockAuthConfig.getUserToken(Constants.systemUser),
        )
        bcMvcHelper.registerUser()
        assertThat(figiProxy).isNotNull
        enrichmentFactory.register(DefaultEnricher())
        Mockito.`when`(fxClientService.getRates(any()))
            .thenReturn(FxResponse(FxPairResults()))
    }

    @Test
    fun is_BuyHouse() {
        val house = AssetInput.toRealEstate(USD, "NY Apartment")
        val houseAsset = assetService.handle(
            AssetRequest(
                mapOf(Pair(house.code, house)),
            ),
        ).data[house.code]
        assertThat(houseAsset).isNotNull
        val portfolio = bcMvcHelper.portfolio(PortfolioInput("RE-TEST"))
        val purchase = TrnInput(
            callerRef = CallerRef(),
            assetId = houseAsset!!.id,
            trnType = TrnType.BUY,
            tradeAmount = tenK,
            tradeCashRate = BigDecimal.ONE,
        )

        val oneK = BigDecimal("1000")
        val nOneK = BigDecimal.ZERO - oneK
        val reduce = TrnInput(
            callerRef = CallerRef(),
            assetId = houseAsset.id,
            trnType = TrnType.REDUCE,
            tradeAmount = oneK,
            tradeCashRate = BigDecimal.ONE,
        )

        val increase = TrnInput(
            callerRef = CallerRef(),
            assetId = houseAsset.id,
            trnType = TrnType.INCREASE,
            tradeAmount = oneK,
            tradeCashRate = BigDecimal.ONE,
        )

        val trns = trnService.save(
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
            .extracting("tradeAmount", "cashAmount")
            .containsExactly(tenK, BigDecimal.ZERO.minus(tenK))

        val r = iterator.next()
        assertThat(r)
            .extracting("tradeAmount", "quantity", "cashAmount")
            .containsExactly(nOneK, nOneK, BigDecimal.ZERO)

        val i = iterator.next()
        assertThat(i)
            .extracting("tradeAmount", "quantity", "cashAmount")
            .containsExactly(oneK, oneK, BigDecimal.ZERO)
    }
}
