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
    fun `should accumulate FX buy transaction correctly`() {
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
        // With cost tracking enabled for FX transactions, we now track cost basis
        assertThat(usdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(
                "costBasis",
                BigDecimal("2500.00") // Trade currency tracks cost (rate defaults to 1.0)
            ).hasFieldOrPropertyWithValue(
                "marketValue",
                ZERO
            ) // Not yet valued

        // Cost basis should be tracked in BASE currency (rate defaults to 1.0)
        assertThat(usdPosition.moneyValues[Position.In.BASE])
            .hasFieldOrPropertyWithValue(
                "costBasis",
                BigDecimal("2500.00") // quantity * rate (2500 * 1.0)
            )

        val nzdPosition = positions.positions[toKey(nzdCashBalance)]
        assertThat(nzdPosition!!.quantityValues)
            .hasFieldOrPropertyWithValue(
                "total",
                trn.cashAmount
            )
        // NZD position now tracks cost basis for FX transactions
        assertThat(nzdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(
                "costBasis",
                BigDecimal("5000.00") // abs(cashAmount) = 5000
            ).hasFieldOrPropertyWithValue(
                "costValue",
                BigDecimal("-5000.00") // Cost value based on quantity and average cost
            ).hasFieldOrPropertyWithValue(
                "marketValue",
                ZERO
            ) // Not yet valued
    }

    @Test
    fun `should track cost basis for FX transactions`() {
        // Test FX: Buy USD 2500 with NZD 3750
        val trn =
            Trn(
                trnType = TrnType.FX_BUY,
                asset = usdCashBalance,
                quantity = BigDecimal("2500.00"),
                cashAsset = nzdCashBalance,
                cashCurrency = NZD,
                tradeCashRate = BigDecimal("1.5"), // 1.5 NZD per USD
                cashAmount = BigDecimal("-3750.00"), // 2500 * 1.5 = 3750 NZD cost
                tradeBaseRate = BigDecimal("1.2"), // USD to NZD base rate
                tradePortfolioRate = BigDecimal("0.8") // USD to portfolio rate
            )
        val positions = Positions()
        val usdPosition = accumulator.accumulate(trn, positions)

        // For FX transactions, we should track cost in base currency (NZD)
        val usdBaseMoneyValues = usdPosition.moneyValues[Position.In.BASE]!!
        assertThat(usdBaseMoneyValues)
            .hasFieldOrPropertyWithValue("purchases", BigDecimal("3000.00")) // 2500 * 1.2
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("3000.00"))

        // NZD cash position should also track the cost (1:1 for cash currency)
        val nzdPosition = positions.positions[toKey(nzdCashBalance)]!!
        assertThat(nzdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("3750.00"))
            .hasFieldOrPropertyWithValue("sales", BigDecimal("-3750.00")) // Negative because it's money going out
    }

    @Test
    fun `should not track cost basis for simple deposits`() {
        // Test simple deposit: no cost calculation needed
        val trn =
            Trn(
                trnType = TrnType.DEPOSIT,
                asset = usdCashBalance,
                quantity = BigDecimal("1000.00"),
                cashCurrency = USD
            )
        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)

        // Simple cash deposits should not have cost tracking
        assertThat(position.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue("costBasis", ZERO)
            .hasFieldOrPropertyWithValue("costValue", ZERO)
            .hasFieldOrPropertyWithValue("purchases", BigDecimal("1000.00"))
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