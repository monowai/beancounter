package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
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
    private val buyBehaviour = BuyBehaviour()
    private val splitBehaviour = SplitBehaviour()
    private val sellBehaviour = SellBehaviour()

    @Test
    fun `should handle quantity correctly for stock split`() {
        val apple =
            getTestAsset(
                NASDAQ,
                AAPL
            )
        val positions = Positions()

        val tradeAmount = BigDecimal("2000")
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = apple,
                quantity = hundred,
                tradeAmount = tradeAmount
            )

        val position =
            buyBehaviour.accumulate(
                buyTrn,
                positions
            )
        val totalField = "total"
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                totalField,
                hundred
            )
        val stockSplit =
            Trn(
                trnType = TrnType.SPLIT,
                asset = apple,
                quantity = BigDecimal("7")
            )
        splitBehaviour.accumulate(
            stockSplit,
            positions
        )

        // 7 for one split
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                totalField,
                BigDecimal(700)
            )
        val costBasis =
            Objects
                .requireNonNull(
                    position.getMoneyValues(
                        Position.In.TRADE,
                        position.asset.market.currency
                    )
                ).costBasis
        assertThat(
            position.getMoneyValues(
                Position.In.TRADE,
                position.asset.market.currency
            )
        ).hasFieldOrPropertyWithValue(
            "costBasis",
            costBasis
        )

        // Another buy at the adjusted price
        buyBehaviour.accumulate(
            buyTrn,
            positions
        )

        val eightHundred = BigDecimal(800)
        // 7 for one split
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                totalField,
                eightHundred
            )

        // Sell the entire position
        sellBehaviour.accumulate(
            Trn(
                trnType = TrnType.SELL,
                asset = apple,
                quantity = eightHundred,
                tradeAmount = tradeAmount
            ),
            positions
        )
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                totalField,
                BigDecimal.ZERO
            )

        // Repurchase; total should be equal to the quantity we just purchased
        buyBehaviour.accumulate(
            buyTrn,
            positions
        )
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                totalField,
                buyTrn.quantity
            )
    }

    private fun split(ratio: String) =
        Trn(
            trnType = TrnType.SPLIT,
            asset = getTestAsset(NASDAQ, AAPL),
            quantity = BigDecimal(ratio)
        )

    private fun trade(
        trnType: TrnType,
        quantity: String
    ) = Trn(
        trnType = trnType,
        asset = getTestAsset(NASDAQ, AAPL),
        quantity = BigDecimal(quantity),
        tradeAmount = BigDecimal("2000")
    )

    @Test
    fun `second split should compound on the first`() {
        val positions = Positions()
        val position = buyBehaviour.accumulate(trade(TrnType.BUY, "100"), positions)
        splitBehaviour.accumulate(split("2"), positions)
        splitBehaviour.accumulate(split("3"), positions)

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("600")
        val tradeMoney = position.getMoneyValues(Position.In.TRADE)
        assertThat(tradeMoney.costBasis).isEqualByComparingTo("2000")
        assertThat(tradeMoney.costValue).isEqualByComparingTo("2000")
    }

    @Test
    fun `split should apply to everything held, including earlier splits, buys and sales`() {
        val positions = Positions()
        val position = buyBehaviour.accumulate(trade(TrnType.BUY, "100"), positions)
        splitBehaviour.accumulate(split("2"), positions)
        buyBehaviour.accumulate(trade(TrnType.BUY, "100"), positions)
        sellBehaviour.accumulate(trade(TrnType.SELL, "50"), positions)
        splitBehaviour.accumulate(split("2"), positions)

        // (100 x 2 + 100 - 50) x 2
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("500")
    }

    @Test
    fun `reverse split after a split should shrink the whole holding`() {
        val positions = Positions()
        val position = buyBehaviour.accumulate(trade(TrnType.BUY, "100"), positions)
        splitBehaviour.accumulate(split("4"), positions)
        splitBehaviour.accumulate(split("0.1"), positions)

        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo("40")
    }
}