package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.PROP_AVERAGE_COST
import com.beancounter.position.Constants.Companion.PROP_COST_BASIS
import com.beancounter.position.Constants.Companion.PROP_REALIZED_GAIN
import com.beancounter.position.Constants.Companion.PROP_SALES
import com.beancounter.position.Constants.Companion.PROP_TOTAL
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.twoK
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * assert money values functionality.
 */
@SpringBootTest(classes = [Accumulator::class])
internal class MoneyValueTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    private lateinit var microsoft: Asset
    private lateinit var intel: Asset
    private lateinit var bidu: Asset
    private lateinit var positions: Positions

    @BeforeEach
    fun setup() {
        microsoft =
            getTestAsset(
                NASDAQ,
                "MSFT",
            )
        intel =
            getTestAsset(
                NASDAQ,
                "INTC",
            )
        bidu =
            getTestAsset(
                NASDAQ,
                "BIDU",
            )
        positions = Positions(getPortfolio())
    }

    @Test
    fun `Realized gain is calculated correctly after sale`() {
        setupBuyTransaction()
        val sellTransaction =
            createSellTransaction(
                BigDecimal(50),
                BigDecimal(2000),
            )
        val position =
            accumulator.accumulate(
                sellTransaction,
                positions,
            )

        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal(50))
        assertThat(position.getMoneyValues(Position.In.TRADE).realisedGain)
            .isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun `Quantity and market value are calculated correctly for purchase`() {
        val result = setupBuyTransaction()
        assertThat(result.quantityValues)
            .hasFieldOrPropertyWithValue(
                "purchased",
                hundred,
            ).hasFieldOrPropertyWithValue(
                "total",
                hundred,
            )
    }

    private fun setupBuyTransaction(): Position {
        val buyTransaction =
            Trn(
                trnType = TrnType.BUY,
                asset = microsoft,
                quantity = hundred,
                tradeAmount = twoK,
            )
        return accumulator.accumulate(
            buyTransaction,
            positions,
        )
    }

    private fun createSellTransaction(
        quantity: BigDecimal,
        tradeAmount: BigDecimal,
    ): Trn =
        Trn(
            trnType = TrnType.SELL,
            asset = microsoft,
            quantity = quantity,
            tradeAmount = tradeAmount,
        )

    private val costBasis = BigDecimal("2100.23")

    val gainValue = BigDecimal("211.56")

    @Test
    fun is_RealisedGainWithSignedQuantitiesCalculated() {
        val positions = Positions()
        var buy =
            Trn(
                trnType = TrnType.BUY,
                asset = bidu,
                quantity = BigDecimal(8),
                tradeAmount = BigDecimal("1695.02"),
            )
        val position =
            accumulator.accumulate(
                buy,
                positions,
            )
        buy =
            Trn(
                trnType = TrnType.BUY,
                asset = bidu,
                quantity = BigDecimal(2),
                tradeAmount = BigDecimal("405.21"),
            )
        accumulator.accumulate(
            buy,
            positions,
        )
        val tradeMoney = position.getMoneyValues(Position.In.TRADE)
        assertThat(
            position.quantityValues
                .getTotal()
                .multiply(tradeMoney.averageCost)
                .setScale(
                    2,
                    RoundingMode.HALF_UP,
                ),
        ).isEqualTo(tradeMoney.costBasis)
        val sell =
            Trn(
                trnType = TrnType.SELL,
                asset = bidu,
                quantity = BigDecimal(-3),
                tradeAmount = BigDecimal("841.63"),
            )
        accumulator.accumulate(
            sell,
            positions,
        )
        assertThat(
            position.quantityValues
                .getTotal()
                .multiply(tradeMoney.averageCost)
                .setScale(
                    2,
                    RoundingMode.HALF_UP,
                ),
        ).isEqualTo(tradeMoney.costValue)
        assertThat(tradeMoney)
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                costBasis,
            ).hasFieldOrPropertyWithValue(
                PROP_SALES,
                sell.tradeAmount,
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                gainValue,
            )
        accumulator.accumulate(
            Trn(
                trnType = TrnType.SELL,
                asset = bidu,
                quantity = BigDecimal(-7),
                tradeAmount = BigDecimal("1871.01"),
            ),
            positions,
        )
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_SALES,
                BigDecimal("2712.64"),
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                BigDecimal("612.41"),
            )
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `Realized gain after selling to zero is correctly calculated`() {
        val positions = Positions(getPortfolio())
        val initialBuys =
            listOf(
                Trn(
                    trnType = TrnType.BUY,
                    asset = microsoft,
                    quantity = BigDecimal(8),
                    tradeAmount = BigDecimal("1695.02"),
                ),
                Trn(
                    trnType = TrnType.BUY,
                    asset = microsoft,
                    quantity = BigDecimal(2),
                    tradeAmount = BigDecimal("405.21"),
                ),
            )
        initialBuys.forEach { buy ->
            accumulator.accumulate(
                buy,
                positions,
            )
        }

        val position = positions.getOrCreate(microsoft)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(
                PROP_TOTAL,
                BigDecimal.TEN,
            )

        val expectedAverageCost = BigDecimal("210.023")

        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                costBasis,
            ).hasFieldOrPropertyWithValue(
                PROP_AVERAGE_COST,
                expectedAverageCost,
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                BigDecimal.ZERO,
            )

        var sell =
            Trn(
                trnType = TrnType.SELL,
                asset = microsoft,
                quantity = BigDecimal(3),
                tradeAmount = BigDecimal("841.63"),
            )

        accumulator.accumulate(
            sell,
            positions,
        )

        // Sell does not affect the cost basis or average cost, but it will create a signed gain
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                costBasis,
            ).hasFieldOrPropertyWithValue(
                PROP_AVERAGE_COST,
                expectedAverageCost,
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                gainValue,
            )

        sell =
            Trn(
                trnType = TrnType.SELL,
                asset = microsoft,
                quantity = BigDecimal(7),
                tradeAmount = BigDecimal("1871.01"),
            )
        accumulator.accumulate(
            sell,
            positions,
        )

        // Sell down to 0; reset cost basis
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_AVERAGE_COST,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                BigDecimal("612.41"),
            )
    }

    @Test
    fun `Realized gain after reentering a position is correctly calculated`() {
        val positions = Positions(getPortfolio())
        val intelPosition = positions.getOrCreate(intel)
        val quantity = BigDecimal(80)
        accumulator.accumulate(
            Trn(
                trnType = TrnType.BUY,
                asset = intel,
                quantity = quantity,
                tradeAmount = BigDecimal("2646.08"),
            ),
            positions,
        )
        accumulator.accumulate(
            Trn(
                trnType = TrnType.SELL,
                asset = intel,
                quantity = quantity,
                tradeAmount = BigDecimal("2273.9"),
            ),
            positions,
        )
        assertThat(intelPosition.quantityValues)
            .hasFieldOrPropertyWithValue(
                PROP_TOTAL,
                BigDecimal.ZERO,
            )
        val realisedGain = BigDecimal("-372.18")
        assertThat(intelPosition.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_AVERAGE_COST,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                realisedGain,
            )

        // Re-enter the position
        val buy =
            Trn(
                trnType = TrnType.BUY,
                asset = intel,
                quantity = BigDecimal(60),
                tradeAmount = BigDecimal("1603.32"),
            )
        accumulator.accumulate(
            buy,
            positions,
        )
        assertThat(intelPosition.quantityValues)
            .hasFieldOrPropertyWithValue(
                PROP_TOTAL,
                buy.quantity,
            )
        assertThat(intelPosition.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                buy.tradeAmount,
            ).hasFieldOrPropertyWithValue(
                PROP_AVERAGE_COST,
                BigDecimal("26.722"),
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                realisedGain,
            )

        // Second sell taking us back to zero. Verify that the accumulated gains.
        val tradeMoney = intelPosition.getMoneyValues(Position.In.TRADE)
        assertThat(tradeMoney).isNotNull
        val previousGain = tradeMoney.realisedGain // Track the previous gain
        accumulator.accumulate(
            Trn(
                trnType = TrnType.SELL,
                asset = intel,
                quantity = BigDecimal(60),
                tradeAmount = BigDecimal("1664.31"),
            ),
            positions,
        )
        assertThat(intelPosition.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(
                PROP_COST_BASIS,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_AVERAGE_COST,
                BigDecimal.ZERO,
            ).hasFieldOrPropertyWithValue(
                PROP_REALIZED_GAIN,
                previousGain.add(BigDecimal("60.99")),
            )
    }
}
