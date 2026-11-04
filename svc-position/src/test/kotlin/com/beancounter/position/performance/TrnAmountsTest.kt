package com.beancounter.position.performance

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * A trn's amounts in its portfolio's currency, converted through the rates stored on
 * the trn. One rule for all of them: no stored trade -> portfolio rate leaves the
 * amount unconverted, and every result is money at 2 dp.
 */
class TrnAmountsTest {
    private val sgdPortfolio =
        Portfolio(id = "sgd-pf", code = "SGDPF", name = "SGD", currency = SGD, base = SGD, owner = owner)
    private val usdPortfolio =
        Portfolio(id = "usd-pf", code = "USDPF", name = "USD", currency = USD, base = USD, owner = owner)
    private val fund = Asset(code = "FUND", id = "FUND-id", name = "Fund", market = Market("US", USD.code))

    @Test
    fun `should leave the cash leg unconverted when no trade to portfolio rate is stored`() {
        val trn = buy(cashAmount = "-100", tradePortfolioRate = BigDecimal.ZERO, portfolio = sgdPortfolio)

        assertThat(trn.cashLegIn(sgdPortfolio)).isEqualByComparingTo("-100")
    }

    @Test
    fun `should scale a cash leg already in portfolio currency to money`() {
        val trn = buy(cashAmount = "-100.125", tradePortfolioRate = BigDecimal.ONE, portfolio = usdPortfolio)

        val cashLeg = trn.cashLegIn(usdPortfolio)

        assertThat(cashLeg).isEqualByComparingTo("-100.13")
        assertThat(cashLeg.scale()).isEqualTo(2)
    }

    @Test
    fun `should scale a trade amount already in portfolio currency to money`() {
        val trn = buy(cashAmount = "-100.125", tradePortfolioRate = BigDecimal.ONE, portfolio = usdPortfolio)

        val tradeAmount = trn.tradeAmountIn(usdPortfolio)

        assertThat(tradeAmount).isEqualByComparingTo("100.13")
        assertThat(tradeAmount.scale()).isEqualTo(2)
    }

    @Test
    fun `should convert the cash leg at the stored trade to portfolio rate`() {
        val trn = buy(cashAmount = "-100", tradePortfolioRate = BigDecimal("1.35"), portfolio = sgdPortfolio)

        assertThat(trn.cashLegIn(sgdPortfolio)).isEqualByComparingTo("-135")
    }

    private fun buy(
        cashAmount: String,
        tradePortfolioRate: BigDecimal,
        portfolio: Portfolio
    ) = Trn(
        trnType = TrnType.BUY,
        asset = fund,
        quantity = BigDecimal.ONE,
        tradeAmount = BigDecimal(cashAmount).abs(),
        cashAsset = usdCashBalance,
        cashCurrency = USD,
        cashAmount = BigDecimal(cashAmount),
        tradeCurrency = USD,
        tradeCashRate = BigDecimal.ONE,
        tradePortfolioRate = tradePortfolioRate,
        portfolio = portfolio
    )
}