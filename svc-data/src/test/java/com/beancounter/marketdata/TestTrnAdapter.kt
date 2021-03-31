package com.beancounter.marketdata

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.toKey
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.usdValue
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

@SpringBootTest(classes = [TrnAdapter::class, TradeCalculator::class])
/**
 * TRN Adapter tests.
 */
internal class TestTrnAdapter {

    @MockBean
    private lateinit var portfolioService: PortfolioService

    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnAdapter: TrnAdapter

    final val asset = MSFT
    final val price: BigDecimal = BigDecimal("10.99")
    private final val theDate = DateUtils().getDate("2019-10-10")
    private val one = "1"

    private val priceProp = "price"

    private val tradeDateProp = "tradeDate"
    private val versionProp = "version"
    private val quantityProp = "quantity"

    @Test
    fun is_BuyInputToTrnComputingTradeAmount() {
        val trnInput = TrnInput(
            CallerRef("ABC", one, one),
            asset.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            price = price,
            tradeCurrency = usdValue,
            tradeDate = theDate,
            comments = "Comment",
        )
        // TradeAmount should be computed for a buy
        trnInput.cashAsset = toKey("USD-X", "USER")
        trnInput.settleDate = theDate
        trnInput.cashAmount = BigDecimal("100.99")
        trnInput.tradeBaseRate = BigDecimal("1.99")
        trnInput.tradeCashRate = BigDecimal("1.99")
        trnInput.tradePortfolioRate = price
        trnInput.tradeBaseRate = BigDecimal.ONE
        trnInput.cashCurrency = usdValue

        val portfolioCode = "abc"
        val trnRequest = TrnRequest(portfolioCode, arrayOf(trnInput))
        Mockito.`when`(portfolioService.find(portfolioCode))
            .thenReturn(getPortfolio(portfolioCode))
        Mockito.`when`(assetService.find(trnInput.assetId))
            .thenReturn(MSFT)
        Mockito.`when`(currencyService.getCode(usdValue))
            .thenReturn(Currency(usdValue))
        val trnResponse = trnAdapter.convert(portfolioService.find(portfolioCode), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue(quantityProp, trnInput.quantity)
            .hasFieldOrPropertyWithValue(tradeDateProp, trnInput.tradeDate)
            .hasFieldOrPropertyWithValue("settleDate", trnInput.settleDate)
            .hasFieldOrPropertyWithValue("fees", trnInput.fees)
            .hasFieldOrPropertyWithValue("cashAmount", trnInput.cashAmount)
            .hasFieldOrPropertyWithValue(priceProp, trnInput.price)
            .hasFieldOrPropertyWithValue(quantityProp, trnInput.quantity)
            .hasFieldOrPropertyWithValue(versionProp, one)
            .hasFieldOrPropertyWithValue("tradeBaseRate", trnInput.tradeBaseRate)
            .hasFieldOrPropertyWithValue("tradeCashRate", trnInput.tradeCashRate)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", trnInput.tradePortfolioRate)
            .hasFieldOrPropertyWithValue("tradeBaseRate", trnInput.tradeBaseRate)
            .hasFieldOrPropertyWithValue("tradeCurrency.code", trnInput.tradeCurrency)
            .hasFieldOrPropertyWithValue("cashCurrency.code", trnInput.cashCurrency)
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("109.90"))
            .hasFieldOrPropertyWithValue("trnType", trnInput.trnType)
            .hasFieldOrPropertyWithValue("comments", trnInput.comments)
    }

    @Test
    fun is_DiviInputToTrnComputingTradeAmount() {
        val tradeAmount = BigDecimal("12.22")
        val trnInput = TrnInput(
            CallerRef("ABC", "1", "1"),
            asset.id,
            trnType = TrnType.DIVI,
            quantity = BigDecimal.TEN,
            price = price,
            tradeAmount = tradeAmount,
        )

        val trnRequest = TrnRequest("abc", arrayOf(trnInput))
        Mockito.`when`(portfolioService.find("abc"))
            .thenReturn(getPortfolio("abc"))
        Mockito.`when`(assetService.find(trnInput.assetId))
            .thenReturn(MSFT)
        Mockito.`when`(currencyService.getCode(usdValue))
            .thenReturn(Currency(usdValue))
        val trnResponse = trnAdapter.convert(portfolioService.find("abc"), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue(quantityProp, trnInput.quantity)
            .hasFieldOrPropertyWithValue(tradeDateProp, trnInput.tradeDate)
            .hasFieldOrPropertyWithValue(priceProp, trnInput.price)
            .hasFieldOrPropertyWithValue(quantityProp, trnInput.quantity)
            .hasFieldOrPropertyWithValue(versionProp, one)
            .hasFieldOrPropertyWithValue("tradeAmount", tradeAmount)
            .hasFieldOrPropertyWithValue("trnType", trnInput.trnType)
            .hasFieldOrPropertyWithValue("comments", trnInput.comments)
    }

    @Test
    fun is_TradeAmountOverridingComputedValue() {
        val trnInput = TrnInput(
            CallerRef("ABC", "1", "1"),
            asset.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            price = price,
            tradeAmount = BigDecimal("88.88"),
        )

        val trnRequest = TrnRequest("abc", arrayOf(trnInput))
        Mockito.`when`(portfolioService.find("abc"))
            .thenReturn(getPortfolio("abc"))
        Mockito.`when`(assetService.find(trnInput.assetId))
            .thenReturn(getAsset("NASDAQ", "MSFT"))
        Mockito.`when`(currencyService.getCode(usdValue))
            .thenReturn(Currency(usdValue))
        val trnResponse = trnAdapter.convert(portfolioService.find("abc"), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue(quantityProp, trnInput.quantity)
            .hasFieldOrPropertyWithValue(tradeDateProp, trnInput.tradeDate)
            .hasFieldOrPropertyWithValue(priceProp, trnInput.price)
            .hasFieldOrPropertyWithValue(quantityProp, trnInput.quantity)
            .hasFieldOrPropertyWithValue(versionProp, one)
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("88.88"))
            .hasFieldOrPropertyWithValue("trnType", trnInput.trnType)
            .hasFieldOrPropertyWithValue("comments", trnInput.comments)
    }
}
