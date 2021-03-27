package com.beancounter.marketdata

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.AssetUtils.Companion.toKey
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.common.utils.TradeCalculator
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
internal class TestTrnAdapter {
    private val currencyUtils = CurrencyUtils()

    @MockBean
    private lateinit var portfolioService: PortfolioService

    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnAdapter: TrnAdapter

    final val asset = getAsset("NASDAQ", "MSFT")

    @Test
    fun is_BuyInputToTrnComputingTradeAmount() {
        val trnInput = TrnInput(
            CallerRef("ABC", "1", "1"),
            asset.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            price = BigDecimal("10.99"),
            tradeCurrency = "USD",
            tradeDate = DateUtils().getDate("2019-10-10"),
            comments = "Comment",
        )
        // TradeAmount should be computed for a buy
        trnInput.cashAsset = toKey("USD-X", "USER")
        trnInput.settleDate = DateUtils().getDate("2019-10-10")
        trnInput.cashAmount = BigDecimal("100.99")
        trnInput.tradeBaseRate = BigDecimal("1.99")
        trnInput.tradeCashRate = BigDecimal("1.99")
        trnInput.tradePortfolioRate = BigDecimal("10.99")
        trnInput.tradeBaseRate = BigDecimal.ONE
        trnInput.cashCurrency = "USD"

        val trnRequest = TrnRequest("abc", arrayOf(trnInput))
        Mockito.`when`(portfolioService.find("abc"))
            .thenReturn(getPortfolio("abc"))
        Mockito.`when`(assetService.find(trnInput.assetId))
            .thenReturn(getAsset("NASDAQ", "MSFT"))
        Mockito.`when`(currencyService.getCode("USD"))
            .thenReturn(currencyUtils.getCurrency("USD"))
        val trnResponse = trnAdapter.convert(portfolioService.find("abc"), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("tradeDate", trnInput.tradeDate)
            .hasFieldOrPropertyWithValue("settleDate", trnInput.settleDate)
            .hasFieldOrPropertyWithValue("fees", trnInput.fees)
            .hasFieldOrPropertyWithValue("cashAmount", trnInput.cashAmount)
            .hasFieldOrPropertyWithValue("price", trnInput.price)
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("version", "1")
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
        val trnInput = TrnInput(
            CallerRef("ABC", "1", "1"),
            asset.id,
            trnType = TrnType.DIVI,
            quantity = BigDecimal.TEN,
            price = BigDecimal("10.99"),
            tradeAmount = BigDecimal("12.22"),
        )

        val trnRequest = TrnRequest("abc", arrayOf(trnInput))
        Mockito.`when`(portfolioService.find("abc"))
            .thenReturn(getPortfolio("abc"))
        Mockito.`when`(assetService.find(trnInput.assetId))
            .thenReturn(getAsset("NASDAQ", "MSFT"))
        Mockito.`when`(currencyService.getCode("USD"))
            .thenReturn(currencyUtils.getCurrency("USD"))
        val trnResponse = trnAdapter.convert(portfolioService.find("abc"), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("tradeDate", trnInput.tradeDate)
            .hasFieldOrPropertyWithValue("price", trnInput.price)
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("version", "1")
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("12.22"))
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
            price = BigDecimal("10.99"),
            tradeAmount = BigDecimal("88.88"),
        )

        val trnRequest = TrnRequest("abc", arrayOf(trnInput))
        Mockito.`when`(portfolioService.find("abc"))
            .thenReturn(getPortfolio("abc"))
        Mockito.`when`(assetService.find(trnInput.assetId))
            .thenReturn(getAsset("NASDAQ", "MSFT"))
        Mockito.`when`(currencyService.getCode("USD"))
            .thenReturn(currencyUtils.getCurrency("USD"))
        val trnResponse = trnAdapter.convert(portfolioService.find("abc"), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("tradeDate", trnInput.tradeDate)
            .hasFieldOrPropertyWithValue("price", trnInput.price)
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("version", "1")
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("88.88"))
            .hasFieldOrPropertyWithValue("trnType", trnInput.trnType)
            .hasFieldOrPropertyWithValue("comments", trnInput.comments)
    }
}
