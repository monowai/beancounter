package com.beancounter.position.valuation

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.PROP_AVERAGE_COST
import com.beancounter.position.Constants.Companion.PROP_CURRENCY
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.US
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.Helper.Companion.convert
import com.beancounter.position.valuation.Helper.Companion.deposit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Verify cash service valuation capabilities.
 */
@SpringBootTest(
    classes = [
        Accumulator::class,
    ],
)
@Nested
class CashValuationTests {
    @Autowired
    private lateinit var accumulator: Accumulator

    @Autowired
    private lateinit var currencyResolver: CurrencyResolver

    val usdCash = getTestAsset(code = USD.code, market = Constants.CASH)
    val nzdCash = getTestAsset(code = NZD.code, market = Constants.CASH)
    val sgdCash = getTestAsset(code = SGD.code, market = Constants.CASH)

    @Test
    fun averageCostOfCash() {
        val usdBalance = BigDecimal("10000.00")
        val sgdBalance = BigDecimal("20000.00")
        val rate = usdBalance.divide(sgdBalance)

        val positions = Positions(portfolio = Portfolio(code = "CostOfCash", currency = SGD, base = SGD))

        accumulator.accumulate(
            convert(
                credit = usdCash,
                creditAmount = usdBalance,
                debit = sgdCash,
                debitAmount = sgdBalance,
                portfolio = positions.portfolio,
                tradeBaseRate = rate,
                tradePortfolioRate = rate,
            ),
            positions,
        )
        val usdPosition = positions.getOrCreate(usdCash)
        assertThat(usdPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(PROP_AVERAGE_COST, BigDecimal.ONE)
            .hasFieldOrPropertyWithValue(PROP_CURRENCY, USD)
        assertThat(usdPosition.moneyValues[Position.In.PORTFOLIO])
            .hasFieldOrPropertyWithValue(PROP_AVERAGE_COST, rate)
            .hasFieldOrPropertyWithValue(PROP_CURRENCY, positions.portfolio.currency)
        assertThat(usdPosition.moneyValues[Position.In.BASE])
            .hasFieldOrPropertyWithValue(PROP_AVERAGE_COST, rate)
            .hasFieldOrPropertyWithValue(PROP_CURRENCY, positions.portfolio.base)
    }

    @Test
    fun `cost In Reference Currencies reflects the cost of cash of the cashAsset`() {
        val usdBalance = BigDecimal("1000.00")
        val expectedRate = BigDecimal("0.5")

        val positions = Positions(portfolio = Portfolio(code = "AssetCost", currency = SGD, base = SGD))

        accumulator.accumulate(
            deposit(
                usdCash,
                balance = usdBalance,
                portfolio = positions.portfolio,
                tradeBaseRate = expectedRate,
                tradePortfolioRate = expectedRate,
            ),
            positions,
        )
        val testAsset = getTestAsset(US, "AnyAsset")
        accumulator.accumulate(
            Trn(
                callerRef = CallerRef(),
                asset = testAsset,
                portfolio = positions.portfolio,
                trnType = TrnType.BUY,
                quantity = usdBalance,
                cashCurrency = USD,
                cashAsset = usdCash,
                tradeAmount = usdBalance,
                price = BigDecimal.ONE,
            ),
            positions,
        )
        val equityPosition = positions.getOrCreate(testAsset)
        assertThat(equityPosition.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(PROP_AVERAGE_COST, BigDecimal.ONE)
            .hasFieldOrPropertyWithValue(PROP_CURRENCY, USD)
        assertThat(equityPosition.moneyValues[Position.In.PORTFOLIO])
            .hasFieldOrPropertyWithValue(PROP_AVERAGE_COST, expectedRate)
            .hasFieldOrPropertyWithValue(PROP_CURRENCY, positions.portfolio.currency)
        assertThat(equityPosition.moneyValues[Position.In.BASE])
            .hasFieldOrPropertyWithValue(PROP_AVERAGE_COST, expectedRate)
            .hasFieldOrPropertyWithValue(PROP_CURRENCY, positions.portfolio.base)
    }

    @Test
    fun cashLadderBalancesWithFx() {
        val positions = Positions(portfolio = Portfolio(code = "FxCashFlows", currency = USD, base = NZD))
        // Initial Deposit
        val nzdBalance = BigDecimal("12000.00")
        accumulator.accumulate(
            deposit(nzdCash, nzdBalance, portfolio = positions.portfolio),
            positions,
        )
        assertThat(positions.isMixedCurrencies).isFalse()
        assertThat(positions.positions).hasSize(1)
        val sgdBalance = BigDecimal("11365.32")
        accumulator.accumulate(
            convert(
                credit = sgdCash,
                creditAmount = sgdBalance,
                debit = nzdCash,
                debitAmount = nzdBalance,
                portfolio = positions.portfolio,
            ),
            positions,
        )
        assertThat(positions.positions).containsKeys(toKey(nzdCash), toKey(sgdCash))
        // NZD Balance has been Sold
        assertThat(positions.getOrCreate(nzdCash).quantityValues.getTotal().compareTo(BigDecimal.ZERO)).isEqualTo(0)
        // SGD Balance has been purchased
        assertThat(positions.getOrCreate(sgdCash).quantityValues.getTotal().compareTo(sgdBalance)).isEqualTo(0)
        // Swap SGD for USD
        val usdBalance = BigDecimal("8359.43")
        accumulator.accumulate(
            convert(
                credit = usdCash,
                creditAmount = usdBalance,
                debit = sgdCash,
                debitAmount = sgdBalance,
                portfolio = positions.portfolio,
            ),
            positions,
        )
        // Verify cash balance buckets.
        assertThat(positions.getOrCreate(nzdCash).quantityValues.getTotal().compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(positions.getOrCreate(sgdCash).quantityValues.getTotal().compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(positions.getOrCreate(usdCash).quantityValues.getTotal().compareTo(usdBalance)).isEqualTo(0)
        // Withdraw the entire balance and check that cost is 0
        accumulator.accumulate(
            Trn(
                trnType = TrnType.WITHDRAWAL,
                asset = usdCash,
                cashAsset = usdCash,
                price = BigDecimal.ONE,
                // Amount to receive
                quantity = usdBalance.multiply(BigDecimal("-1")),
            ),
            positions,
        )
        val usdPosition = positions.getOrCreate(usdCash)
        assertThat(usdPosition.quantityValues.getTotal().compareTo(BigDecimal.ZERO)).isEqualTo(0)
        usdPosition.moneyValues.values.forEach {
            assertThat(it.averageCost.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        }
    }
}
