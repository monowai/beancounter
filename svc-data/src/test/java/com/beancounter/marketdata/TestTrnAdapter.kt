package com.beancounter.marketdata

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.fromKey
import com.beancounter.common.utils.AssetUtils.Companion.toKey
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
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
import java.util.ArrayList

@SpringBootTest(classes = [TrnAdapter::class])
internal class TestTrnAdapter {
    private val currencyUtils = CurrencyUtils()

    @MockBean
    private val portfolioService: PortfolioService? = null

    @MockBean
    private val assetService: AssetService? = null

    @MockBean
    private val currencyService: CurrencyService? = null

    @Autowired
    private val trnAdapter: TrnAdapter? = null

    @Test
    fun is_InputToTrn() {
        val trnInput = TrnInput(
            CallerRef("ABC", "1", "1"),
            toKey("MSFT", "NASDAQ"),
            TrnType.BUY, BigDecimal.TEN
        )
        trnInput.cashAsset = toKey("USD-X", "USER")
        trnInput.tradeDate = DateUtils().getDate("2019-10-10")
        trnInput.settleDate = DateUtils().getDate("2019-10-10")
        trnInput.fees = BigDecimal.ONE
        trnInput.cashAmount = BigDecimal("100.99")
        trnInput.tradeAmount = BigDecimal("100.99")
        trnInput.price = BigDecimal("10.99")
        trnInput.tradeBaseRate = BigDecimal("1.99")
        trnInput.tradeCashRate = BigDecimal("1.99")
        trnInput.tradePortfolioRate = BigDecimal("10.99")
        trnInput.tradeBaseRate = BigDecimal.ONE
        trnInput.tradeCurrency = "USD"
        trnInput.cashCurrency = "USD"
        trnInput.comments = "Comment"
        val trnInputCollection: MutableCollection<TrnInput> = ArrayList()
        trnInputCollection.add(trnInput)
        val trnRequest = TrnRequest("abc", trnInputCollection)
        Mockito.`when`(portfolioService!!.find("abc"))
            .thenReturn(getPortfolio("abc"))
        Mockito.`when`(assetService!!.find(trnInput.assetId))
            .thenReturn(fromKey(trnInput.assetId))
        Mockito.`when`(currencyService!!.getCode("USD"))
            .thenReturn(currencyUtils.getCurrency("USD"))
        val trnResponse = trnAdapter!!.convert(portfolioService.find("abc"), trnRequest)
        assertThat(trnResponse).isNotNull
        assertThat(trnResponse.data).hasSize(1)
        assertThat(trnResponse.data.iterator().next())
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("tradeDate", trnInput.tradeDate)
            .hasFieldOrPropertyWithValue("settleDate", trnInput.settleDate)
            .hasFieldOrPropertyWithValue("fees", trnInput.fees)
            .hasFieldOrPropertyWithValue("cashAmount", trnInput.cashAmount)
            .hasFieldOrPropertyWithValue("tradeAmount", trnInput.tradeAmount)
            .hasFieldOrPropertyWithValue("price", trnInput.price)
            .hasFieldOrPropertyWithValue("quantity", trnInput.quantity)
            .hasFieldOrPropertyWithValue("version", "1")
            .hasFieldOrPropertyWithValue("tradeBaseRate", trnInput.tradeBaseRate)
            .hasFieldOrPropertyWithValue("tradeCashRate", trnInput.tradeCashRate)
            .hasFieldOrPropertyWithValue("tradePortfolioRate", trnInput.tradePortfolioRate)
            .hasFieldOrPropertyWithValue("tradeBaseRate", trnInput.tradeBaseRate)
            .hasFieldOrPropertyWithValue("tradeCurrency.code", trnInput.tradeCurrency)
            .hasFieldOrPropertyWithValue("cashCurrency.code", trnInput.cashCurrency)
            .hasFieldOrPropertyWithValue("trnType", trnInput.trnType)
            .hasFieldOrPropertyWithValue("comments", trnInput.comments)
    }
}
