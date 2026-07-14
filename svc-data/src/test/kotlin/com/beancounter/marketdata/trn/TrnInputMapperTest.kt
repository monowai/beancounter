package com.beancounter.marketdata.trn

import com.beancounter.client.FxService
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Broker
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Market
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.systemUser
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerSettlementAccount
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketConfig
import com.beancounter.marketdata.portfolio.PortfolioService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.TEN
import java.math.BigDecimal.ZERO
import java.util.Locale
import java.util.Optional

/**
 * TRN Adapter tests.
 */
@SpringBootTest(
    classes = [
        TrnInputMapper::class,
        TradeCalculator::class,
        CashTrnServices::class,
        MarketConfig::class,
        KeyGenUtils::class
    ]
)
internal class TrnInputMapperTest {
    @MockitoBean
    private lateinit var portfolioService: PortfolioService

    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var assetFinder: AssetFinder

    @MockitoBean
    private lateinit var currencyService: CurrencyService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @MockitoBean
    private lateinit var fxService: FxService

    @MockitoBean
    private lateinit var brokerRepository: com.beancounter.marketdata.broker.BrokerRepository

    @MockitoBean
    private lateinit var brokerSettlementAccountRepository:
        com.beancounter.marketdata.broker.BrokerSettlementAccountRepository

    @Autowired
    private lateinit var trnInputMapper: TrnInputMapper

    val asset = MSFT
    val price: BigDecimal = BigDecimal("10.99")
    private val theDate = DateUtils().getFormattedDate("2019-10-10")
    private val one = "1"
    val portfolioId = "abc"

    private val priceProp = "price"

    private val tradeDateProp = "tradeDate"
    private val versionProp = "version"
    private val quantityProp = "quantity"

    private val trnTypeProp = "trnType"
    private val commentsProp = "comments"

    private val tradeAmountProp = "tradeAmount"

    @BeforeEach
    fun mockResponses() {
        Mockito
            .`when`(portfolioService.find(portfolioId))
            .thenReturn(getPortfolio(portfolioId))
        Mockito
            .`when`(assetService.find(asset.id))
            .thenReturn(MSFT)
        Mockito
            .`when`(assetService.find("USD-X:USER"))
            .thenReturn(usdCashBalance)
        Mockito
            .`when`(assetFinder.find("USD-X:USER"))
            .thenReturn(usdCashBalance)
        Mockito
            .`when`(assetFinder.find(asset.id))
            .thenReturn(MSFT)
        Mockito
            .`when`(currencyService.getCode(USD.code))
            .thenReturn(USD)
        Mockito
            .`when`(currencyService.getCode(NZD.code))
            .thenReturn(NZD)
    }

    @Test
    fun `buy calcs amount and market currency`() {
        val trnInput =
            TrnInput(
                CallerRef(
                    portfolioId.uppercase(Locale.getDefault()),
                    one,
                    one
                ),
                assetId = asset.id,
                trnType = TrnType.BUY,
                quantity = TEN,
                price = price,
                cashAssetId =
                    toKey(
                        "USD-X",
                        "USER"
                    ),
                tradeDate = theDate,
                cashAmount = BigDecimal("100.99"),
                cashCurrency = USD.code,
                tradeCashRate = BigDecimal("1.99"),
                tradePortfolioRate = price,
                tradeBaseRate = ONE,
                comments = "Comment"
            )
        // TradeAmount should be computed for a buy
        trnInput.settleDate = theDate

        val trnRequest =
            TrnRequest(
                portfolioId,
                listOf(trnInput)
            )
        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                trnRequest
            )
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.iterator().next())
            .hasFieldOrPropertyWithValue("tradeCurrency", USD)
            .hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                tradeDateProp,
                trnInput.tradeDate
            ).hasFieldOrPropertyWithValue(
                "settleDate",
                trnInput.settleDate
            ).hasFieldOrPropertyWithValue(
                "fees",
                trnInput.fees
            ).hasFieldOrPropertyWithValue(
                "cashAmount",
                trnInput.cashAmount
            ).hasFieldOrPropertyWithValue(
                priceProp,
                trnInput.price
            ).hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                versionProp,
                Trn.VERSION
            ).hasFieldOrPropertyWithValue(
                "tradeBaseRate",
                trnInput.tradeBaseRate
            ).hasFieldOrPropertyWithValue(
                "tradeCashRate",
                trnInput.tradeCashRate
            ).hasFieldOrPropertyWithValue(
                "tradePortfolioRate",
                trnInput.tradePortfolioRate
            ).hasFieldOrPropertyWithValue(
                "tradeBaseRate",
                trnInput.tradeBaseRate
            ).hasFieldOrPropertyWithValue(
                "tradeCurrency.code",
                USD.code // Default to market currency if not supplied
            ).hasFieldOrPropertyWithValue(
                "cashAsset.priceSymbol",
                USD.code
            ).hasFieldOrPropertyWithValue(
                "cashCurrency.code",
                USD.code
            ).hasFieldOrPropertyWithValue(
                tradeAmountProp,
                BigDecimal("109.90")
            ).hasFieldOrPropertyWithValue(
                trnTypeProp,
                trnInput.trnType
            ).hasFieldOrPropertyWithValue(
                commentsProp,
                trnInput.comments
            )
    }

    @Test
    fun diviInputToTrnComputingTradeAmount() {
        val tradeAmount = BigDecimal("12.22")
        val trnInput =
            TrnInput(
                CallerRef(
                    portfolioId.uppercase(Locale.getDefault()),
                    one,
                    one
                ),
                asset.id,
                trnType = TrnType.DIVI,
                quantity = TEN,
                price = price,
                tradeBaseRate = ONE,
                tradeCashRate = ZERO,
                tradePortfolioRate = ONE,
                tradeAmount = tradeAmount
            )

        val trnRequest =
            TrnRequest(
                portfolioId,
                listOf(trnInput)
            )
        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                trnRequest
            )
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.iterator().next())
            .hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                tradeDateProp,
                trnInput.tradeDate
            ).hasFieldOrPropertyWithValue(
                priceProp,
                trnInput.price
            ).hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                versionProp,
                Trn.VERSION
            ).hasFieldOrPropertyWithValue(
                tradeAmountProp,
                tradeAmount
            ).hasFieldOrPropertyWithValue(
                trnTypeProp,
                trnInput.trnType
            ).hasFieldOrPropertyWithValue(
                commentsProp,
                trnInput.comments
            )
    }

    @Test
    fun tradeAmountOverridingComputedValue() {
        val tradeAmount = BigDecimal("88.88")
        val trnInput =
            TrnInput(
                CallerRef(
                    portfolioId.uppercase(Locale.getDefault()),
                    one,
                    one
                ),
                asset.id,
                trnType = TrnType.BUY,
                tradeCurrency = NZD.code, // Overrides asset market currency
                quantity = TEN,
                price = price,
                tradeBaseRate = ONE,
                tradeCashRate = ZERO,
                tradePortfolioRate = ONE,
                tradeAmount = tradeAmount
            )

        val trnRequest =
            TrnRequest(
                portfolioId,
                listOf(trnInput)
            )
        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                trnRequest
            )
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.iterator().next())
            .hasFieldOrPropertyWithValue("tradeCurrency", NZD) // overrides market
            .hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                tradeDateProp,
                trnInput.tradeDate
            ).hasFieldOrPropertyWithValue(
                priceProp,
                trnInput.price
            ).hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                versionProp,
                Trn.VERSION
            ).hasFieldOrPropertyWithValue(
                tradeAmountProp,
                tradeAmount
            ).hasFieldOrPropertyWithValue(
                trnTypeProp,
                trnInput.trnType
            ).hasFieldOrPropertyWithValue(
                commentsProp,
                trnInput.comments
            )
    }

    @Test
    fun splitInputToTrnComputingTradeAmount() {
        val trnInput =
            TrnInput(
                CallerRef(
                    portfolioId.uppercase(Locale.getDefault()),
                    one,
                    one
                ),
                asset.id,
                trnType = TrnType.SPLIT,
                quantity = TEN,
                price = price
            )

        val trnRequest =
            TrnRequest(
                portfolioId,
                listOf(trnInput)
            )
        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                trnRequest
            )
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.iterator().next())
            .hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                tradeDateProp,
                trnInput.tradeDate
            ).hasFieldOrPropertyWithValue(
                priceProp,
                trnInput.price
            ).hasFieldOrPropertyWithValue(
                quantityProp,
                trnInput.quantity
            ).hasFieldOrPropertyWithValue(
                versionProp,
                Trn.VERSION
            ).hasFieldOrPropertyWithValue(
                trnTypeProp,
                trnInput.trnType
            ).hasFieldOrPropertyWithValue(
                commentsProp,
                trnInput.comments
            )
    }

    @Test
    fun `POLICY balance overrides client-supplied tradeCurrency with accountingType currency`() {
        // CPF (POLICY) is statutory SGD. A buggy client sending tradeCurrency=USD
        // for a SGD-denominated CPF asset must NOT bake USD into the trn —
        // svc-position would otherwise multiply by USD->SGD on the PORTFOLIO
        // bucket, inflating CPF balance by ~28% in net worth and projections.
        val cpfAsset =
            Asset(
                code = "userId.CPF",
                id = "asset-cpf",
                name = "CPF",
                market = Market("PRIVATE"),
                category = "POLICY",
                assetCategory =
                    AssetCategory(
                        id = "POLICY",
                        name = "Retirement Fund"
                    ),
                accountingType =
                    AccountingType(
                        id = "policy-sgd",
                        category = "POLICY",
                        currency = SGD
                    )
            )
        Mockito.`when`(assetFinder.find(cpfAsset.id)).thenReturn(cpfAsset)
        Mockito.`when`(currencyService.getCode(SGD.code)).thenReturn(SGD)

        val trnInput =
            TrnInput(
                CallerRef(portfolioId.uppercase(Locale.getDefault()), one, one),
                assetId = cpfAsset.id,
                trnType = TrnType.BALANCE,
                tradeCurrency = USD.code, // wrong — client bug
                quantity = BigDecimal("281000"),
                price = ONE,
                tradeAmount = BigDecimal("281000"),
                tradeBaseRate = ONE,
                tradeCashRate = ZERO,
                tradePortfolioRate = ONE
            )

        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                TrnRequest(portfolioId, listOf(trnInput))
            )

        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.first())
            .hasFieldOrPropertyWithValue("tradeCurrency", SGD)
    }

    @Test
    fun `should default settlement to broker account when cash currency omitted`() {
        // #1040: a SELL saved with brokerId but no cashCurrency/cashAssetId used to
        // resolve cashAsset = null (CashTrnServices.getCashAsset needs BOTH brokerId
        // AND cashCurrency for the broker settlement-account tier) — silently
        // dropping the compensating auto-settle transfer. The mapper must default
        // the settlement currency to the trade currency so the broker's configured
        // per-currency account is found.
        val broker = Broker(id = "broker-1", name = "IBKR", owner = systemUser)
        Mockito
            .`when`(brokerRepository.findById(broker.id))
            .thenReturn(Optional.of(broker))
        Mockito
            .`when`(
                brokerSettlementAccountRepository.findByBrokerIdAndCurrencyCode(
                    broker.id,
                    USD.code
                )
            ).thenReturn(
                BrokerSettlementAccount(
                    broker = broker,
                    currencyCode = USD.code,
                    account = usdCashBalance
                )
            )

        val trnInput =
            TrnInput(
                CallerRef(
                    portfolioId.uppercase(Locale.getDefault()),
                    one,
                    one
                ),
                assetId = asset.id, // MSFT, market currency USD
                trnType = TrnType.SELL,
                brokerId = broker.id,
                quantity = TEN,
                price = price,
                tradeBaseRate = ONE,
                tradeCashRate = ONE,
                tradePortfolioRate = ONE
            )

        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                TrnRequest(portfolioId, listOf(trnInput))
            )

        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.first())
            .hasFieldOrPropertyWithValue("cashAsset.id", usdCashBalance.id)
            .hasFieldOrPropertyWithValue("cashCurrency", USD)
    }

    @Test
    fun `should not default cash currency for FX_BUY`() {
        // FX_BUY's cash leg currency is the SOLD currency, not the trade (bought)
        // currency — defaulting to trade currency here would corrupt it. When no
        // cashCurrency/cashAssetId is supplied, FX_BUY must keep today's behaviour:
        // no broker settlement-account tier is consulted, cashAsset stays null.
        val broker = Broker(id = "broker-fx", name = "FX-BROKER", owner = systemUser)
        Mockito
            .`when`(brokerRepository.findById(broker.id))
            .thenReturn(Optional.of(broker))

        val trnInput =
            TrnInput(
                CallerRef(
                    portfolioId.uppercase(Locale.getDefault()),
                    one,
                    one
                ),
                assetId = asset.id,
                trnType = TrnType.FX_BUY,
                brokerId = broker.id,
                quantity = TEN,
                price = price,
                tradeBaseRate = ONE,
                tradeCashRate = ONE,
                tradePortfolioRate = ONE
            )

        val trnResponse =
            trnInputMapper.convert(
                portfolioService.find(portfolioId),
                TrnRequest(portfolioId, listOf(trnInput))
            )

        assertThat(trnResponse).hasSize(1)
        assertThat(trnResponse.first().cashAsset).isNull()
        assertThat(trnResponse.first().cashCurrency).isNull()
    }
}