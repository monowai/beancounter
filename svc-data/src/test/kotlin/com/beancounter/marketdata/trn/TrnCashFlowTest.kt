package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
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
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.fx.FxRateService
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
 * Validate miscellaneous transaction scenarios that impact cash.
 */
@SpringMvcDbTest
class TrnCashFlowTest {
    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

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

    private lateinit var bcMvcHelper: BcMvcHelper

    val usNzRate = BigDecimal("1.5")
    val tenK = BigDecimal("10000.00")
    private val twoFiveK = "2500.00"
    private val pQuantity = "quantity"
    private lateinit var aapl: Asset

    private val propTradeAmount = "tradeAmount"
    private val propCashAmount = "cashAmount"
    private lateinit var nzCashAsset: Asset
    private lateinit var usCashAsset: Asset

    @Autowired
    fun setupObjects(assetService: AssetService) {
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                mockAuthConfig.getUserToken(Constants.systemUser)
            )
        bcMvcHelper.registerUser()
        assertThat(figiProxy).isNotNull
        Mockito
            .`when`(
                fxClientService.getRates(
                    any(),
                    any()
                )
            ).thenReturn(FxResponse(FxPairResults()))
        nzCashAsset = getCashBalance(Constants.NZD)
        usCashAsset = getCashBalance(Constants.USD)
        aapl =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(
                            Constants.NASDAQ.code,
                            Constants.AAPL.code
                        ),
                        Constants.AAPL.code
                    )
                ).data[Constants.AAPL.code]!!
    }

    @Test
    fun fxNzTrn() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "FXNZ",
                    base = "NZD",
                    currency = "USD"
                )
            )
        // FX Buy USD
        val fxTrn =
            trnService.save(
                portfolio,
                TrnRequest(
                    portfolio.id,
                    arrayOf(
                        TrnInput(
                            callerRef = CallerRef(),
                            trnType = TrnType.FX_BUY,
                            // Buying
                            assetId = usCashAsset.id,
                            tradeAmount = BigDecimal(twoFiveK),
                            // Selling
                            cashAssetId = nzCashAsset.id,
                            cashAmount = BigDecimal("-5000.00"),
                            // US/NZ
                            tradePortfolioRate = usNzRate,
                            // US/NZ
                            tradeCashRate = usNzRate,
                            tradeBaseRate = usNzRate,
                            price = BigDecimal.ONE
                        )
                    )
                )
            )
        assertThat(fxTrn.iterator().next())
            .hasFieldOrPropertyWithValue(
                pQuantity,
                BigDecimal(twoFiveK)
            )
        validateTrnSerialization(fxTrn)
    }

    @Test
    fun nzTrn() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "NZTRN",
                    base = "NZD",
                    currency = "USD"
                )
            )
        // Let's start with a base currency deposit
        val nzTrn =
            trnService.save(
                portfolio,
                TrnRequest(
                    portfolio.id,
                    arrayOf(
                        TrnInput(
                            callerRef = CallerRef(),
                            trnType = TrnType.DEPOSIT,
                            assetId = nzCashAsset.id,
                            tradeAmount = tenK,
                            cashAssetId = nzCashAsset.id,
                            cashAmount = tenK,
                            tradePortfolioRate = BigDecimal.ONE,
                            tradeCashRate = BigDecimal.ONE,
                            tradeBaseRate = BigDecimal.ONE,
                            price = BigDecimal.ONE
                        )
                    )
                )
            )
        assertThat(nzTrn.iterator().next())
            .hasFieldOrPropertyWithValue(
                pQuantity,
                tenK
            )
        validateTrnSerialization(nzTrn)
    }

    @Test
    fun assetPurchaseAgainstUsdBalance() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "USDP",
                    base = "NZD",
                    currency = "USD"
                )
            )
        val nzUsRate =
            MathUtils.divide(
                BigDecimal.ONE,
                usNzRate
            )
        val buyTrn =
            trnService.save(
                portfolio,
                TrnRequest(
                    portfolio.id,
                    arrayOf(
                        TrnInput(
                            callerRef = CallerRef(),
                            assetId = aapl.id,
                            cashAssetId = nzCashAsset.id,
                            tradeCashRate = nzUsRate,
                            tradeBaseRate = nzUsRate,
                            quantity = BigDecimal("1000.00"),
                            price = BigDecimal.ONE
                        )
                    )
                )
            )
        assertThat(buyTrn.iterator().next())
            .hasFieldOrPropertyWithValue(
                propTradeAmount,
                BigDecimal("1000.00")
            ).hasFieldOrPropertyWithValue(
                "cashAsset.id",
                nzCashAsset.id
            ).hasFieldOrPropertyWithValue(
                "cashCurrency.code",
                Constants.NZD.code
            ).hasFieldOrPropertyWithValue(
                propCashAmount,
                BigDecimal("-1492.54")
            )
        validateTrnSerialization(buyTrn)
    }

    private fun validateTrnSerialization(buyTrn: Collection<Trn>) {
        assertThat(
            objectMapper.writeValueAsString(
                TrnResponse(
                    arrayListOf(
                        buyTrn.iterator().next()
                    )
                )
            )
        ).isNotNull
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