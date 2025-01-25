package com.beancounter.position.valuation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.position.Constants.Companion.CASH
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.accumulation.AccumulationStrategy
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.CashAccumulator
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.accumulation.WithdrawalBehaviour
import com.beancounter.position.utils.CurrencyResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AltCashCurrencyTests {
    private val buyBehaviour: AccumulationStrategy = BuyBehaviour()
    private val sellBehaviour: AccumulationStrategy = SellBehaviour()
    private val portfolio = Portfolio(id = "id", code = "TEST", currency = USD, base = NZD)
    private val asset =
        getTestAsset(
            NASDAQ,
            "ABC"
        )
    private val cashAsset = Asset(market = CASH, code = NZD.code)

    @Test
    fun `buy usd Asset settle in nzd`() {
        val usdTradeAmount = BigDecimal("800.00")
        val nzdTradeCash = BigDecimal("-1600")
        val tradeCashRate = nzdTradeCash.divide(usdTradeAmount).abs()
        val positions = Positions(portfolio)
        val accumulator =
            Accumulator(
                TrnBehaviourFactory(
                    listOf(
                        buyBehaviour,
                        sellBehaviour,
                        WithdrawalBehaviour(
                            CashAccumulator(currencyResolver = CurrencyResolver())
                        )
                    )
                )
            )
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = hundred,
                tradeAmount = usdTradeAmount,
                tradeCurrency = USD,
                cashCurrency = NZD,
                cashAsset = cashAsset,
                cashAmount = nzdTradeCash,
                tradeBaseRate = tradeCashRate,
                tradeCashRate = tradeCashRate,
                portfolio = portfolio
            )
        accumulator.accumulate(trn, positions)

        assertThat(positions.positions.size).isEqualTo(2)

        assertThat(positions.getOrThrow(asset).getMoneyValues(Position.In.BASE))
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal("16.00"))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("1600.00"))
            .hasFieldOrPropertyWithValue("costValue", BigDecimal("1600.00"))
    }
}