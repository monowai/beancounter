package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.position.Constants.Companion.CASH
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.nzdCashBalance
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO

/**
 * Buy the trade asset, sell the cash asset.
 */
@SpringBootTest(classes = [Accumulator::class])
class FxBuyBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    @Test
    fun is_FxBuyAccumulated() {
        val trn =
            Trn(
                trnType = TrnType.FX_BUY,
                asset = usdCashBalance,
                // Buy
                quantity = BigDecimal("2500.00"),
                cashAsset = nzdCashBalance,
                cashCurrency = NZD,
                // Sell
                tradeCashRate = BigDecimal("1.5"),
                cashAmount = BigDecimal("-5000.00")
            )
        val positions = Positions()
        val usdPosition =
            accumulator.accumulate(
                trn,
                positions
            )
        // Fx affects two positions
        assertThat(positions.positions).containsKeys(
            toKey(usdCashBalance),
            toKey(nzdCashBalance)
        )
        // Primary Position
        assertThat(usdPosition.asset).isEqualTo(usdCashBalance)
        assertThat(usdPosition.quantityValues)
            .hasFieldOrPropertyWithValue(
                "total",
                trn.tradeAmount
            )
        assertThat(usdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(
                "costBasis",
                ZERO
            ).hasFieldOrPropertyWithValue(
                "marketValue",
                ZERO
            ) // Not yet valued

        val nzdPosition = positions.positions[toKey(nzdCashBalance)]
        assertThat(nzdPosition!!.quantityValues)
            .hasFieldOrPropertyWithValue(
                "total",
                trn.cashAmount
            )
        assertThat(nzdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(
                "costBasis",
                ZERO
            ).hasFieldOrPropertyWithValue(
                "costValue",
                ZERO
            ).hasFieldOrPropertyWithValue(
                "marketValue",
                ZERO
            ) // Not yet valued
    }

    @Test
    fun `sgd Cash Balance after USD FX Buy`() {
        val sgdCashAsset =
            Asset(
                code = "${SGD.code} ${TrnType.BALANCE}",
                id = "${SGD.code} ${TrnType.BALANCE}",
                name = "${SGD.code} Balance",
                market = Market(CASH.code),
                priceSymbol = SGD.code,
                category = CASH.code
            )
        assertThat(sgdCashAsset).isNotNull
        val sgPortfolio = Portfolio(id = "blah", code = "sgdThing", base = NZD, currency = USD)
        val sgdCash = BigDecimal("8756.00")
        val deposit =
            Trn(
                callerRef = CallerRef(),
                asset = sgdCashAsset,
                cashAsset = sgdCashAsset,
                portfolio = sgPortfolio,
                cashCurrency = SGD,
                tradeCurrency = SGD,
                tradeCashRate = BigDecimal("1.2677"),
                tradeBaseRate = BigDecimal("1.2677"),
                tradePortfolioRate = BigDecimal("0.7427"),
                quantity = sgdCash,
                cashAmount = sgdCash,
                price = ONE,
                trnType = TrnType.DEPOSIT
            )
        val fxBuy =
            Trn(
                callerRef = CallerRef(),
                asset = usdCashBalance,
                trnType = TrnType.FX_BUY,
                cashAsset = sgdCashAsset,
                portfolio = sgPortfolio,
                cashCurrency = sgdCashAsset.market.currency,
                tradeCurrency = usdCashBalance.market.currency,
                quantity = BigDecimal("5822.00"),
                cashAmount = BigDecimal("-8000.00")
            )
        val positions = Positions(sgPortfolio)
        val sgdDeposit =
            accumulator.accumulate(
                deposit,
                positions
            )
        accumulator.accumulate(
            fxBuy,
            positions
        )
        assertThat(sgdDeposit).isNotNull
        val moneyValues = sgdDeposit.moneyValues
        assertThat(moneyValues).hasSize(3)
        assertThat(moneyValues[Position.In.TRADE]?.currency).isEqualTo(SGD)
        assertThat(moneyValues[Position.In.PORTFOLIO]?.currency).isEqualTo(USD)
        assertThat(moneyValues[Position.In.BASE]?.currency).isEqualTo(NZD)
    }
}