package com.beancounter.position.accumulation

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils
import com.beancounter.position.Constants.Companion.AAPL
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.PROP_COST_BASIS
import com.beancounter.position.Constants.Companion.PROP_COST_VALUE
import com.beancounter.position.Constants.Companion.PROP_PURCHASES
import com.beancounter.position.Constants.Companion.PROP_SOLD
import com.beancounter.position.Constants.Companion.PROP_TOTAL
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO

/**
 * Verifies the expected behaviour of a Cash position for supported transaction types.
 */
@SpringBootTest(classes = [Accumulator::class])
internal class CashBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator
    private val cashAmount = BigDecimal("-10000.00")

    @Test
    fun `should accumulate deposit correctly`() {
        val trn =
            Trn(
                trnType = TrnType.DEPOSIT,
                asset = usdCashBalance,
                quantity = BigDecimal("10000.00"),
                // Buy
                cashCurrency = USD,
                portfolio = PortfolioUtils.getPortfolio()
            )
        val positions = Positions()
        val position =
            accumulator.accumulate(
                trn,
                positions
            )
        assertThat(
            position.getMoneyValues(
                Position.In.TRADE,
                Currency(usdCashBalance.priceSymbol!!)
            )
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            ZERO
        ).hasFieldOrPropertyWithValue(
            PROP_PURCHASES,
            trn.quantity
        )

        assertThat(position.quantityValues).hasFieldOrPropertyWithValue(
            "precision",
            2
        )
    }

    @Test
    fun `should accumulate deposit for sell transaction`() {
        val asset =
            getTestAsset(
                NASDAQ,
                AAPL
            )
        val trn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = ONE,
                tradeAmount = ONE,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                // Cash is signed
                cashAmount = cashAmount.abs(),
                portfolio = PortfolioUtils.getPortfolio()
            )

        val positions = Positions()
        val position =
            accumulator.accumulate(
                trn,
                positions
            )
        assertThat(position).hasFieldOrPropertyWithValue(
            "asset",
            asset
        )
        assertThat(positions.positions).hasSize(2)
        val cashPosition = positions.getOrCreate(usdCashBalance)
        assertThat(cashPosition.quantityValues)
            .hasFieldOrPropertyWithValue(
                "purchased",
                cashAmount.abs()
            )
        // SELL transactions now track cost basis for cash received
        assertThat(
            cashPosition.getMoneyValues(
                Position.In.TRADE,
                Currency(usdCashBalance.priceSymbol!!)
            )
        ).hasFieldOrPropertyWithValue(
            PROP_COST_BASIS,
            cashAmount.abs() // Cost basis equals the cash amount for trade settlements
        )
    }

    @Test
    fun `should accumulate withdrawal correctly`() {
        val trn =
            Trn(
                trnType = TrnType.WITHDRAWAL,
                asset = usdCashBalance,
                quantity = cashAmount,
                // Cash is signed
                cashCurrency = USD,
                portfolio = PortfolioUtils.getPortfolio()
            )
        val positions = Positions()
        val position =
            accumulator.accumulate(
                trn,
                positions
            )
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                PROP_SOLD,
                trn.quantity
            ).hasFieldOrPropertyWithValue(
                PROP_TOTAL,
                trn.quantity
            )
        assertThat(
            position.getMoneyValues(
                Position.In.TRADE,
                Currency(usdCashBalance.priceSymbol!!)
            )
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            ZERO
        ).hasFieldOrPropertyWithValue(
            "sales",
            trn.quantity
        )
    }

    @Test
    fun `should accumulate withdrawal for buy transaction`() {
        val asset =
            getTestAsset(
                NASDAQ,
                AAPL
            )
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = ONE,
                tradeAmount = ONE,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = cashAmount,
                portfolio = PortfolioUtils.getPortfolio()
            )
        val positions = Positions()
        val position =
            accumulator.accumulate(
                trn,
                positions
            )
        assertThat(position).hasFieldOrPropertyWithValue(
            "asset",
            asset
        )
        assertThat(positions.positions).hasSize(2)
        val cashPosition = positions.getOrCreate(usdCashBalance)
        assertThat(cashPosition.quantityValues)
            .hasFieldOrPropertyWithValue(
                PROP_SOLD,
                cashAmount
            )
        assertThat(
            cashPosition.getMoneyValues(
                Position.In.TRADE,
                Currency(usdCashBalance.priceSymbol!!)
            )
        ).hasFieldOrPropertyWithValue(
            PROP_PURCHASES,
            ZERO
        )
    }
}