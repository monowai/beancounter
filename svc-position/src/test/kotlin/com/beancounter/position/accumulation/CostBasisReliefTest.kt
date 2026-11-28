package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.TEST
import com.beancounter.position.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal

/**
 * Average cost accounting: a disposal takes its share of the cost out of the position, so the
 * cost basis is always the cost of what is still held, in every reporting currency.
 */
class CostBasisReliefTest {
    private val asset = Asset("TEST", market = Constants.US)
    private val portfolio = Portfolio(TEST, currency = NZD, base = SGD)
    private val baseRate = BigDecimal("2")
    private val portfolioRate = BigDecimal("3")
    private val behaviours: Map<TrnType, AccumulationStrategy> =
        listOf(BuyBehaviour(), SellBehaviour(), ReduceBehaviour(), SplitBehaviour(), CostAdjustBehaviour())
            .associateBy { it.supportedType }

    private fun Positions.apply(
        trnType: TrnType,
        quantity: String,
        tradeAmount: String = "0",
        tradeBaseRate: BigDecimal = baseRate
    ): Position =
        behaviours.getValue(trnType).accumulate(
            Trn(
                asset = asset,
                trnType = trnType,
                quantity = BigDecimal(quantity),
                tradeAmount = BigDecimal(tradeAmount),
                tradeCurrency = USD,
                tradeBaseRate = tradeBaseRate,
                tradePortfolioRate = portfolioRate,
                portfolio = portfolio
            ),
            this
        )

    private fun Position.trade(): MoneyValues = getMoneyValues(Position.In.TRADE, USD)

    private fun Position.base(): MoneyValues = getMoneyValues(Position.In.BASE, SGD)

    private fun Position.inPortfolio(): MoneyValues = getMoneyValues(Position.In.PORTFOLIO, NZD)

    private fun assertCost(
        moneyValues: MoneyValues,
        cost: String,
        unitCost: String
    ) {
        assertThat(moneyValues.costBasis).isEqualByComparingTo(cost)
        assertThat(moneyValues.costValue).isEqualByComparingTo(cost)
        assertThat(moneyValues.averageCost).isEqualByComparingTo(unitCost)
    }

    @ParameterizedTest
    @EnumSource(value = TrnType::class, names = ["SELL", "REDUCE"])
    fun `partial disposal should relieve cost at average cost in every currency`(disposal: TrnType) {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        val position = positions.apply(disposal, "40", "600")

        assertCost(position.trade(), "600", "10")
        assertCost(position.base(), "1200", "20")
        assertCost(position.inPortfolio(), "1800", "30")
        assertThat(position.trade().realisedGain).isEqualByComparingTo("200")
        assertThat(position.base().realisedGain).isEqualByComparingTo("400")
        assertThat(position.inPortfolio().realisedGain).isEqualByComparingTo("600")
    }

    @Test
    fun `disposal without an amount should still relieve cost`() {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        val position = positions.apply(TrnType.REDUCE, "40")

        assertCost(position.trade(), "600", "10")
        assertCost(position.base(), "1200", "20")
        assertThat(position.trade().realisedGain).isEqualByComparingTo("0")
    }

    @Test
    fun `cost should be relieved at the rate it was bought at, not the rate of the sale`() {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        val position = positions.apply(TrnType.SELL, "50", "600", tradeBaseRate = BigDecimal("4"))

        assertCost(position.base(), "1000", "20")
        // Proceeds 600 at 4, less the cost of 50 at 20.
        assertThat(position.base().realisedGain).isEqualByComparingTo("1400")
    }

    @Test
    fun `buy after a partial sale should average over the cost still held`() {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        positions.apply(TrnType.SELL, "50", "600")
        val position = positions.apply(TrnType.BUY, "50", "1000")

        assertCost(position.trade(), "1500", "15")
        assertCost(position.base(), "3000", "30")
        assertCost(position.inPortfolio(), "4500", "45")
    }

    @Test
    fun `realised gain over a closed position should be its sales less its purchases`() {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        positions.apply(TrnType.SELL, "50", "600")
        positions.apply(TrnType.BUY, "50", "1000")
        val position = positions.apply(TrnType.SELL, "100", "2000")

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("0")
        assertThat(position.trade().realisedGain).isEqualByComparingTo("600")
        assertThat(position.base().realisedGain).isEqualByComparingTo("1200")
        assertThat(position.inPortfolio().realisedGain).isEqualByComparingTo("1800")
        assertCost(position.trade(), "0", "0")
    }

    @Test
    fun `split after a partial sale should keep the cost held and restate unit cost`() {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        positions.apply(TrnType.SELL, "50", "600")
        val position = positions.apply(TrnType.SPLIT, "2")

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("100")
        assertCost(position.trade(), "500", "5")
        assertCost(position.base(), "1000", "10")
        assertCost(position.inPortfolio(), "1500", "15")
    }

    @Test
    fun `cost adjust after a partial sale should adjust the cost still held`() {
        val positions = Positions(portfolio)
        positions.apply(TrnType.BUY, "100", "1000")
        positions.apply(TrnType.SELL, "50", "600")
        val position = positions.apply(TrnType.COST_ADJUST, "0", "-50")

        assertCost(position.trade(), "450", "9")
        assertCost(position.base(), "900", "18")
        assertCost(position.inPortfolio(), "1350", "27")
    }

    @ParameterizedTest
    @EnumSource(value = TrnType::class, names = ["SELL", "REDUCE"])
    fun `should reject a disposal of more than is held and leave the position as it was`(disposal: TrnType) {
        val positions = Positions(portfolio)
        val position = positions.apply(TrnType.BUY, "50", "500")

        assertThatThrownBy { positions.apply(disposal, "50.001", "1600") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("50.001")

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("50")
        assertCost(position.trade(), "500", "10")
        assertThat(position.trade().sales).isEqualByComparingTo("0")
    }

    @Test
    fun `should reject a sell when nothing is held`() {
        assertThatThrownBy { Positions(portfolio).apply(TrnType.SELL, "1", "10") }
            .isInstanceOf(BusinessException::class.java)
    }
}