package com.beancounter.marketdata.trn

import com.beancounter.client.FxService
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
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
}