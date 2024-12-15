package com.beancounter.marketdata.cash

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.NZD
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.math.BigDecimal.ONE

/**
 * Test the impact on cash for various transaction scenario flows.
 */

@SpringMvcDbTest
class CashTrnTests {
    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

    private lateinit var bcMvcHelper: BcMvcHelper

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory
    private val fiveK = BigDecimal("5000.00")
    private val propTradeAmount = "tradeAmount"
    private val propCashAmount = "cashAmount"

    @Autowired
    fun setupObjects(
        mockAuthConfig: MockAuthConfig,
        mockMvc: MockMvc
    ) {
        assertThat(fxTransactions).isNotNull
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                mockAuthConfig.getUserToken(Constants.systemUser)
            )
        bcMvcHelper.registerUser()
        assertThat(figiProxy).isNotNull
        enrichmentFactory.register(DefaultEnricher())
        Mockito
            .`when`(
                fxClientService.getRates(
                    any(),
                    any()
                )
            ).thenReturn(FxResponse(FxPairResults()))
    }

    @Test
    fun depositCash() {
        val nzdBalance = BigDecimal("10000.00")
        val tradePortfolioRate = BigDecimal("0.698971")
        val cashInput = AssetUtils.getCash(NZD.code)
        val nzCashAsset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            Pair(
                                NZD.code,
                                cashInput
                            )
                        )
                    )
                ).data[NZD.code]
        assertThat(nzCashAsset).isNotNull
        val usPortfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "depositCash",
                    base = NZD.code,
                    currency = USD.code
                )
            )
        val cashDeposit =
            TrnInput(
                callerRef = CallerRef(),
                assetId = nzCashAsset!!.id,
                // Credit generic cash balance.
                cashCurrency = NZD.code,
                trnType = TrnType.DEPOSIT,
                tradeCashRate = ONE,
                tradeBaseRate = ONE,
                tradePortfolioRate = tradePortfolioRate,
                tradeAmount = nzdBalance,
                price = ONE
            )
        val trns =
            trnService.save(
                usPortfolio,
                TrnRequest(
                    usPortfolio.id,
                    arrayOf(cashDeposit)
                )
            )
        assertThat(trns).isNotNull.hasSize(1)
        val cashTrn = trns.iterator().next()
        assertThat(cashTrn)
            .hasFieldOrPropertyWithValue(
                propTradeAmount,
                cashDeposit.tradeAmount
            ).hasFieldOrPropertyWithValue(
                propCashAmount,
                nzdBalance
            ).hasFieldOrPropertyWithValue(
                "tradeCashRate",
                cashDeposit.tradeCashRate
            ).hasFieldOrPropertyWithValue(
                "tradePortfolioRate",
                tradePortfolioRate
            ).hasFieldOrPropertyWithValue(
                "cashAsset",
                nzCashAsset
            ).hasFieldOrPropertyWithValue(
                "cashCurrency",
                NZD
            )
    }

    @Test
    fun buyDebitsCash() {
        val nzCashAsset = getCashBalance(NZD)
        assertThat(nzCashAsset).isNotNull
        val equity =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(
                            NYSE.code,
                            MSFT.code
                        ),
                        MSFT.code
                    )
                ).data[MSFT.code]
        val usPortfolio = bcMvcHelper.portfolio(PortfolioInput(code = "buyDebitsCash"))
        val buy =
            TrnInput(
                callerRef = CallerRef(),
                assetId = equity!!.id,
                cashCurrency = NZD.code,
                tradeCashRate = BigDecimal("0.50"),
                tradeAmount = fiveK,
                price = ONE
            )
        val trns =
            trnService.save(
                usPortfolio,
                TrnRequest(
                    usPortfolio.id,
                    arrayOf(buy)
                )
            )
        assertThat(trns).isNotNull.hasSize(1)
        val cashTrn = trns.iterator().next()
        assertThat(cashTrn)
            .hasFieldOrPropertyWithValue(
                propTradeAmount,
                buy.tradeAmount
            ).hasFieldOrPropertyWithValue(
                propCashAmount,
                BigDecimal("-10000.00")
            ).hasFieldOrPropertyWithValue(
                "tradeCashRate",
                buy.tradeCashRate
            ).hasFieldOrPropertyWithValue(
                "cashAsset",
                nzCashAsset
            ).hasFieldOrPropertyWithValue(
                "cashCurrency",
                NZD
            )
    }

    fun getCashBalance(currency: Currency): Asset {
        val cashInput = AssetUtils.getCash(currency.code)
        return assetService
            .handle(
                AssetRequest(
                    mapOf(
                        Pair(
                            currency.code,
                            cashInput
                        )
                    )
                )
            ).data[currency.code]!!
    }
}