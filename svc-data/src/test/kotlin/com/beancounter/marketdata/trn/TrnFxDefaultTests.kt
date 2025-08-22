package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.Constants.Companion.CASH_MARKET
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
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

/**
 * Check transformations work for rates
 */
@SpringBootTest(
    classes = [
        TrnAdapter::class,
        TradeCalculator::class,
        CashTrnServices::class,
        MarketConfig::class,
        KeyGenUtils::class
    ]
)
class TrnFxDefaultTests {
    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var assetFinder: AssetFinder

    @MockitoBean
    private lateinit var portfolioService: PortfolioService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @MockitoBean
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnAdapter: TrnAdapter

    private val asset =
        getTestAsset(
            NASDAQ,
            "ABC"
        )
    private val portfolio = Portfolio(id = "id", code = "TEST", currency = USD, base = NZD)

    @BeforeEach
    fun mockResponses() {
        Mockito
            .`when`(portfolioService.find(portfolio.id))
            .thenReturn(portfolio)
        Mockito
            .`when`(assetService.find(asset.id))
            .thenReturn(asset)
        Mockito
            .`when`(assetFinder.find(asset.id))
            .thenReturn(asset)
        Mockito
            .`when`(currencyService.getCode(USD.code))
            .thenReturn(USD)
        Mockito
            .`when`(currencyService.getCode(NZD.code))
            .thenReturn(NZD)

        Mockito
            .`when`(assetService.findOrCreate(AssetInput(market = CASH_MARKET.code, code = NZD.code)))
            .thenReturn(
                Asset(
                    code = "NZD",
                    id = "NZD",
                    name = "NZD Balance",
                    market = CASH_MARKET,
                    marketCode = CASH_MARKET.code,
                    priceSymbol = NZD.code,
                    category = "CASH",
                    assetCategory =
                        AssetCategory(
                            "CASH",
                            "Cash"
                        ),
                    systemUser = SystemUser()
                )
            )
    }

    @Test
    fun `test something`() {
        val usdTradeAmount = BigDecimal("800.00")
        val nzdTradeCash = BigDecimal("-1600")

        val trnInput =
            TrnInput(
                CallerRef(),
                assetId = asset.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal("100.00"),
                tradeCurrency = USD.code,
                tradeBaseRate = BigDecimal.ONE,
                tradeAmount = usdTradeAmount,
                cashAmount = nzdTradeCash,
                cashCurrency = NZD.code,
                comments = "Comment"
            )

        val trns = trnAdapter.convert(portfolio, TrnRequest(portfolio.id, arrayOf(trnInput)))
        assertThat(trns.size).isEqualTo(1)
        val trn = trns[0]
        assertThat(trn)
            .hasFieldOrPropertyWithValue("tradeCashRate", BigDecimal("2.00"))
            .hasFieldOrPropertyWithValue("tradeBaseRate", BigDecimal.ONE)
    }
}