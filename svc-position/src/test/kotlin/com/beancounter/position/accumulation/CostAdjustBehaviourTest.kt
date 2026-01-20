package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.hundred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Tests for CostAdjustBehaviour - adjusts cost basis without changing quantity or cash.
 */
class CostAdjustBehaviourTest {
    private val costAdjustBehaviour = CostAdjustBehaviour()
    private val buyBehaviour = BuyBehaviour()

    private fun createPortfolio(): Portfolio = getPortfolio()

    private fun createAsset(code: String) =
        Asset(
            id = code,
            code = code,
            name = code,
            market = NASDAQ
        )

    @Test
    fun `cost adjust increases cost basis`() {
        val asset = createAsset("TEST")
        val portfolio = createPortfolio()
        val positions = Positions(portfolio)

        // First, buy 10 shares at $100 = $1000 cost basis
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal.TEN,
                tradeAmount = BigDecimal("1000"),
                price = hundred,
                tradeCurrency = portfolio.currency,
                portfolio = portfolio
            )
        val position = Position(asset, portfolio)
        positions.add(position)
        buyBehaviour.accumulate(buyTrn, positions, position)

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.TEN)
        assertThat(position.getMoneyValues(Position.In.TRADE).costBasis)
            .isEqualByComparingTo(BigDecimal("1000"))

        // Now adjust cost basis up by $200 (e.g., wash sale adjustment)
        val adjustTrn =
            Trn(
                trnType = TrnType.COST_ADJUST,
                asset = asset,
                quantity = BigDecimal.ZERO,
                tradeAmount = BigDecimal("200"),
                tradeCurrency = portfolio.currency,
                portfolio = portfolio
            )
        costAdjustBehaviour.accumulate(adjustTrn, positions, position)

        // Quantity unchanged, cost basis increased
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.TEN)
        assertThat(position.getMoneyValues(Position.In.TRADE).costBasis)
            .isEqualByComparingTo(BigDecimal("1200"))
        assertThat(position.getMoneyValues(Position.In.TRADE).averageCost)
            .isEqualByComparingTo(BigDecimal("120"))
    }

    @Test
    fun `cost adjust decreases cost basis for return of capital`() {
        val asset = createAsset("TEST")
        val portfolio = createPortfolio()
        val positions = Positions(portfolio)

        // Buy 10 shares at $100 = $1000 cost basis
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal.TEN,
                tradeAmount = BigDecimal("1000"),
                price = hundred,
                tradeCurrency = portfolio.currency,
                portfolio = portfolio
            )
        val position = Position(asset, portfolio)
        positions.add(position)
        buyBehaviour.accumulate(buyTrn, positions, position)

        // Return of capital reduces cost basis by $150
        val adjustTrn =
            Trn(
                trnType = TrnType.COST_ADJUST,
                asset = asset,
                quantity = BigDecimal.ZERO,
                tradeAmount = BigDecimal("-150"),
                tradeCurrency = portfolio.currency,
                portfolio = portfolio
            )
        costAdjustBehaviour.accumulate(adjustTrn, positions, position)

        // Quantity unchanged, cost basis decreased
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.TEN)
        assertThat(position.getMoneyValues(Position.In.TRADE).costBasis)
            .isEqualByComparingTo(BigDecimal("850"))
        assertThat(position.getMoneyValues(Position.In.TRADE).averageCost)
            .isEqualByComparingTo(BigDecimal("85"))
    }

    @Test
    fun `cost adjust does not impact cash`() {
        assertThat(TrnType.isCashImpacted(TrnType.COST_ADJUST)).isFalse()
    }
}