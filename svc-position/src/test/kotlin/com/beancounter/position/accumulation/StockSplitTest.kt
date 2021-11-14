package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.AAPL
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.hundred
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
internal class StockSplitTest {
    @Test
    fun is_QuantityWorkingForSplit() {
        val apple = getAsset(NASDAQ, AAPL)
        val positions = Positions()
        val position = positions[apple]
        assertThat(position).isNotNull

        val buyTrn = Trn(TrnType.BUY, apple, hundred)
        val tradeAmount = BigDecimal("2000")

        buyTrn.tradeAmount = tradeAmount
        val buyBehaviour = BuyBehaviour()
        buyBehaviour.accumulate(buyTrn, positions.portfolio, position)
        val totalField = "total"
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, hundred)
        val stockSplit = Trn(TrnType.SPLIT, apple, BigDecimal("7"))
        val splitBehaviour = SplitBehaviour()
        splitBehaviour.accumulate(stockSplit, positions.portfolio, position)

        // 7 for one split
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal(700))
        val costBasis = Objects.requireNonNull(
            position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        ).costBasis
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", costBasis)

        // Another buy at the adjusted price
        buyBehaviour.accumulate(buyTrn, positions.portfolio, position)

        // 7 for one split
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal(800))
        val sell = Trn(TrnType.SELL, apple, BigDecimal("800"))
        sell.tradeAmount = tradeAmount

        // Sell the entire position
        SellBehaviour().accumulate(sell, positions.portfolio, position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal.ZERO)

        // Repurchase; total should be equal to the quantity we just purchased
        buyBehaviour.accumulate(buyTrn, getPortfolio(), position)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, buyTrn.quantity)
    }
}
