package com.beancounter.position

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.accumulation.SplitBehaviour
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Objects

/**
 * Corporate Actions - Stock Splits.  These do not affect Cost.
 *
 * @author mikeh
 * @since 2019-02-20
 */
internal class TestStockSplits {
    @Test
    fun is_QuantityWorkingForSplit() {
        val apple = getAsset("NASDAQ", "AAPL")
        val positions = Positions(getPortfolio("TEST"))
        val position = positions[apple]
        assertThat(position).isNotNull

        val buyTrn = Trn(TrnType.BUY, apple, BigDecimal("100"))
        buyTrn.tradeAmount = BigDecimal("2000")
        val buyBehaviour = BuyBehaviour()
        buyBehaviour.accumulate(buyTrn, positions.portfolio, position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal(100))
        val stockSplit = Trn(TrnType.SPLIT, apple, BigDecimal("7"))
        val splitBehaviour = SplitBehaviour()
        splitBehaviour.accumulate(stockSplit, positions.portfolio, position)

        // 7 for one split
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal(700))
        val costBasis = Objects.requireNonNull(
            position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        ).costBasis
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", costBasis)

        // Another buy at the adjusted price
        buyBehaviour.accumulate(buyTrn, positions.portfolio, position)

        // 7 for one split
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal(800))
        val sell = Trn(TrnType.SELL, apple, BigDecimal("800"))
        sell.tradeAmount = BigDecimal("2000")

        // Sell the entire position
        SellBehaviour().accumulate(sell, positions.portfolio, position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO)

        // Repurchase; total should be equal to the quantity we just purchased
        buyBehaviour.accumulate(buyTrn, getPortfolio("TEST"), position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", buyTrn.quantity)
    }
}
