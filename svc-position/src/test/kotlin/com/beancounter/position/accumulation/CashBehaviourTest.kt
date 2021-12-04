package com.beancounter.position.accumulation

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.position.Constants.Companion.AAPL
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest(classes = [Accumulator::class])
class CashBehaviourTest {

    @Autowired
    private lateinit var accumulator: Accumulator
    private val cashAmount = BigDecimal("-10000.00")

    @Test
    fun is_DepositAccumulated() {
        val trn = Trn(
            trnType = TrnType.DEPOSIT,
            asset = usdCashBalance,
            cashCurrency = USD,
            quantity = BigDecimal("10000.00"), // Buy
        )
        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)
        assertThat(position.getMoneyValues(Position.In.TRADE, Currency(usdCashBalance.priceSymbol!!)))
            .hasFieldOrPropertyWithValue("costValue", trn.quantity)
            .hasFieldOrPropertyWithValue("costBasis", trn.quantity)

        assertThat(position.quantityValues).hasFieldOrPropertyWithValue("precision", 2)
    }

    @Test
    fun is_DepositAccumulatedForSell() {
        val asset = getAsset(NASDAQ, AAPL)
        val trn = Trn(
            trnType = TrnType.SELL,
            asset = asset,
            quantity = BigDecimal.ONE,
            tradeAmount = BigDecimal.ONE,
            cashAsset = usdCashBalance,
            cashCurrency = USD,
            cashAmount = cashAmount.abs(), // Cash is signed
        )

        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)
        assertThat(position).hasFieldOrPropertyWithValue("asset", asset)
        assertThat(positions.positions).hasSize(2)
        val cashPosition = positions[usdCashBalance]
        assertThat(cashPosition.quantityValues)
            .hasFieldOrPropertyWithValue("purchased", cashAmount.abs())
        assertThat(cashPosition.getMoneyValues(Position.In.TRADE, Currency(usdCashBalance.priceSymbol!!)))
            .hasFieldOrPropertyWithValue("costBasis", trn.cashAmount)
            .hasFieldOrPropertyWithValue("costValue", trn.cashAmount)
    }

    @Test
    fun is_WithdrawalAccumulated() {
        val trn = Trn(
            trnType = TrnType.WITHDRAWAL,
            asset = usdCashBalance,
            cashCurrency = USD,
            quantity = cashAmount, // Cash is signed
        )
        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("sold", cashAmount)
            .hasFieldOrPropertyWithValue("total", cashAmount)
        assertThat(position.getMoneyValues(Position.In.TRADE, Currency(usdCashBalance.priceSymbol!!)))
            .hasFieldOrPropertyWithValue("costBasis", trn.quantity)
            .hasFieldOrPropertyWithValue("costValue", trn.quantity)
    }

    @Test
    fun is_WithdrawalAccumulatedForBuy() {
        val asset = getAsset(NASDAQ, AAPL)
        val trn = Trn(
            trnType = TrnType.BUY,
            asset = asset,
            quantity = BigDecimal.ONE,
            tradeAmount = BigDecimal.ONE,
            cashAsset = usdCashBalance,
            cashCurrency = USD,
            cashAmount = cashAmount,
        )
        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)
        assertThat(position).hasFieldOrPropertyWithValue("asset", asset)
        assertThat(positions.positions).hasSize(2)
        val cashPosition = positions[usdCashBalance]
        assertThat(cashPosition.quantityValues)
            .hasFieldOrPropertyWithValue("sold", cashAmount)
        assertThat(cashPosition.getMoneyValues(Position.In.TRADE, Currency(usdCashBalance.priceSymbol!!)))
            .hasFieldOrPropertyWithValue("purchases", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("costBasis", cashAmount)
            .hasFieldOrPropertyWithValue("costValue", cashAmount)
    }
}
