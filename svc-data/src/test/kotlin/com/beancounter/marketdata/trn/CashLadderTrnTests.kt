package com.beancounter.marketdata.trn

import com.beancounter.auth.common.TokenUtils
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
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
class CashLadderTrnTests {

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
    val usNzRate = BigDecimal("1.5")

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

    private val fiveK = BigDecimal("5000.00")

    private val propTradeAmount = "tradeAmount"

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
            tradeCashRate = BigDecimal("1.00"),
            tradeAmount = fiveK,
            price = ONE
        )
        val trns = trnService.save(usPortfolio, TrnRequest(usPortfolio.id, arrayOf(cashDeposit)))
        assertThat(trns.data).isNotNull.hasSize(1)
        val cashTrn = trns.data.iterator().next()
        assertThat(cashTrn)
            .hasFieldOrPropertyWithValue(propTradeAmount, cashDeposit.tradeAmount)
            .hasFieldOrPropertyWithValue("cashAmount", fiveK)
            .hasFieldOrPropertyWithValue("tradeCashRate", cashDeposit.tradeCashRate)
            .hasFieldOrPropertyWithValue("cashAsset", nzCashAsset)
            .hasFieldOrPropertyWithValue("cashCurrency", NZD)
    }

    @Test
    fun buyDebitsCash() {
        val nzCashAsset = getCashBalance(NZD)
        assertThat(nzCashAsset).isNotNull
        val equity =
            assetService.process(AssetRequest(MSFT.code, AssetInput(NYSE.code, MSFT.code))).data[MSFT.code]
        val usPortfolio = bcMvcHelper.portfolio(PortfolioInput(code = "buyDebitsCash"))
        val buy = TrnInput(
            callerRef = CallerRef(),
            assetId = equity!!.id,
            cashAssetId = nzCashAsset!!.id,
            tradeCashRate = BigDecimal("0.50"),
            tradeAmount = fiveK,
            price = ONE
        )
        val trns = trnService.save(usPortfolio, TrnRequest(usPortfolio.id, arrayOf(buy)))
        assertThat(trns.data).isNotNull.hasSize(1)
        val cashTrn = trns.data.iterator().next()
        assertThat(cashTrn)
            .hasFieldOrPropertyWithValue(propTradeAmount, buy.tradeAmount)
            .hasFieldOrPropertyWithValue("cashAmount", BigDecimal("-10000.00"))
            .hasFieldOrPropertyWithValue("tradeCashRate", buy.tradeCashRate)
            .hasFieldOrPropertyWithValue("cashAsset", nzCashAsset)
            .hasFieldOrPropertyWithValue("cashCurrency", NZD)
    }

    @Test
    fun cashLadderFlow() {
        val nzCashAsset = getCashBalance(NZD)
        val usCashAsset = getCashBalance(USD)
        val portfolio = bcMvcHelper.portfolio(PortfolioInput(code = "CASHLADDER", base = "NZD", currency = "USD"))

        val equity =
            assetService.process(AssetRequest(AAPL.code, AssetInput(NASDAQ.code, AAPL.code))).data[AAPL.code]

        // Let's start with a base currency deposit
        val nzTrn = trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                arrayOf(
                    TrnInput(
                        callerRef = CallerRef(),
                        trnType = TrnType.DEPOSIT,
                        assetId = nzCashAsset!!.id,
                        tradeAmount = BigDecimal("10000.00"),
                        cashAssetId = nzCashAsset.id,
                        cashAmount = BigDecimal("10000.00"),
                        tradePortfolioRate = ONE,
                        tradeCashRate = ONE,
                        price = ONE
                    )
                )
            )
        )
        assertThat(nzTrn.data.iterator().next())
            .hasFieldOrPropertyWithValue("quantity", BigDecimal("10000.00"))
        // FX Buy USD
        val fxTrn = trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                arrayOf(
                    TrnInput(
                        callerRef = CallerRef(),
                        trnType = TrnType.FX_BUY,
                        assetId = usCashAsset!!.id, // Buying
                        tradeAmount = BigDecimal("2500.00"),
                        cashAssetId = nzCashAsset.id, // Selling
                        cashAmount = BigDecimal("-5000.00"),
                        tradePortfolioRate = usNzRate, // US/NZ
                        tradeCashRate = usNzRate, // US/NZ
                        price = ONE
                    )
                )
            )
        )
        assertThat(fxTrn.data.iterator().next())
            .hasFieldOrPropertyWithValue("quantity", BigDecimal("2500.00"))
        // Asset Purchase against US cash balance
        val nzUsRate = MathUtils.divide(ONE, usNzRate)
        val buyTrn = trnService.save(
            portfolio,
            TrnRequest(
                portfolio.id,
                arrayOf(
                    TrnInput(
                        callerRef = CallerRef(),
                        assetId = equity!!.id,
                        cashAssetId = nzCashAsset.id,
                        tradeCashRate = nzUsRate,
                        quantity = BigDecimal("1000.00"),
                        price = ONE
                    )
                )
            )
        )
        assertThat(buyTrn.data.iterator().next())
            .hasFieldOrPropertyWithValue(propTradeAmount, BigDecimal("1000.00"))
            .hasFieldOrPropertyWithValue("cashAsset.id", nzCashAsset.id)
            .hasFieldOrPropertyWithValue("cashCurrency.code", NZD.code)
            .hasFieldOrPropertyWithValue("cashAmount", BigDecimal("-1492.54"))

        val trnResponse = TrnResponse(
            arrayListOf(
                nzTrn.data.iterator().next(),
                fxTrn.data.iterator().next(),
                buyTrn.data.iterator().next()
            )
        )

        // Output of this is used in cash-ladder-response.json contract
        assertThat(BcJson().objectMapper.writeValueAsString(trnResponse)).isNotNull
    }

    fun getCashBalance(currency: Currency): Asset? {
        val cashInput = AssetUtils.getCash(currency.code)
        return assetService.process(AssetRequest(mapOf(Pair(currency.code, cashInput)))).data[currency.code]
    }
}
