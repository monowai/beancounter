package com.beancounter.marketdata.trn

import com.beancounter.auth.common.TokenUtils
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.MockEnricher
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.math.BigDecimal.ONE

/**
 * Test the impact on cash for various transaction scenario flows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
class CashTrnTests {

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

    private lateinit var bcMvcHelper: BcMvcHelper

    @MockBean
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @BeforeEach
    fun setupObjects() {
        bcMvcHelper = BcMvcHelper(
            MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build(),
            TokenUtils().getUserToken(Constants.systemUser)
        )
        bcMvcHelper.registerUser()
        assertThat(figiProxy).isNotNull
        enrichmentFactory.register(MockEnricher())
    }

    @Test
    fun depositCash() {
        val cashInput = AssetUtils.getCash(NZD.code)
        val nzCashAsset = assetService.process(
            AssetRequest(
                mapOf(Pair(NZD.code, cashInput))
            )
        ).data[NZD.code]
        assertThat(nzCashAsset).isNotNull
        val usPortfolio = bcMvcHelper.portfolio(PortfolioInput(code = "depositCash"))
        val cashDeposit = TrnInput(
            callerRef = CallerRef(),
            assetId = nzCashAsset!!.id,
            cashAssetId = nzCashAsset.id,
            trnType = TrnType.DEPOSIT,
            tradeCashRate = BigDecimal("0.50"),
            tradeAmount = BigDecimal("5000.00"),
            price = ONE
        )
        val trns = trnService.save(usPortfolio, TrnRequest(usPortfolio.id, arrayOf(cashDeposit)))
        assertThat(trns.data).isNotNull.hasSize(1)
        val cashTrn = trns.data.iterator().next()
        assertThat(cashTrn)
            .hasFieldOrPropertyWithValue("tradeAmount", cashDeposit.tradeAmount)
            .hasFieldOrPropertyWithValue("cashAmount", BigDecimal("2500.00"))
            .hasFieldOrPropertyWithValue("tradeCashRate", cashDeposit.tradeCashRate)
            .hasFieldOrPropertyWithValue("cashAsset", nzCashAsset)
            .hasFieldOrPropertyWithValue("cashCurrency", null) // Deprecated
    }

    @Test
    fun buyDebitsCash() {
        val cashInput = AssetUtils.getCash(NZD.code)
        val nzCashAsset = assetService.process(AssetRequest(mapOf(Pair(NZD.code, cashInput)))).data[NZD.code]
        assertThat(nzCashAsset).isNotNull
        val equity =
            assetService.process(AssetRequest(mapOf(Pair(MSFT.code, AssetInput(NYSE.code, MSFT.code))))).data[MSFT.code]
        val usPortfolio = bcMvcHelper.portfolio(PortfolioInput(code = "buyDebitsCash"))
        val buy = TrnInput(
            callerRef = CallerRef(),
            assetId = equity!!.id,
            cashAssetId = nzCashAsset!!.id,
            tradeCashRate = BigDecimal("0.50"),
            tradeAmount = BigDecimal("5000.00"),
            price = ONE
        )
        val trns = trnService.save(usPortfolio, TrnRequest(usPortfolio.id, arrayOf(buy)))
        assertThat(trns.data).isNotNull.hasSize(1)
        val cashTrn = trns.data.iterator().next()
        assertThat(cashTrn)
            .hasFieldOrPropertyWithValue("tradeAmount", buy.tradeAmount)
            .hasFieldOrPropertyWithValue("cashAmount", BigDecimal("-2500.00"))
            .hasFieldOrPropertyWithValue("tradeCashRate", buy.tradeCashRate)
            .hasFieldOrPropertyWithValue("cashAsset", nzCashAsset)
            .hasFieldOrPropertyWithValue("cashCurrency", null)
    }
}
