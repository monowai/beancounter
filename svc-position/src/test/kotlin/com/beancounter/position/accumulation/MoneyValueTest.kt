package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.twoK
import org.assertj.core.api.Assertions.assertThat
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
    private val microsoft = getTestAsset(NASDAQ, "MSFT")
    private val intel = getTestAsset(NASDAQ, "INTC")
    private val bidu = getTestAsset(NASDAQ, "BIDU")

    @Autowired
    private lateinit var accumulator: Accumulator

    private val pSales = "sales"
    private val pCostBasis = "costBasis"

    private val totalField = "total"

    @Test
    fun is_QuantityAndMarketValueCalculated() {
        val positions = Positions(getPortfolio())
        val buy = Trn(trnType = TrnType.BUY, asset = microsoft, quantity = hundred, tradeAmount = twoK)
        assertThat(accumulator.accumulate(buy, positions).quantityValues)
            .hasFieldOrPropertyWithValue("purchased", hundred)
            .hasFieldOrPropertyWithValue(totalField, hundred)
    }

    @Test
    fun is_RealisedGainCalculated() {
        val positions = Positions()
        val buy = Trn(trnType = TrnType.BUY, asset = microsoft, quantity = hundred, tradeAmount = BigDecimal(2000))
        accumulator.accumulate(buy, positions)
        val position = accumulator.accumulate(
            Trn(
                trnType = TrnType.SELL,
                asset = microsoft,
                quantity = BigDecimal(50),
                tradeAmount = BigDecimal(2000),
            ),
            positions,
        )
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.valueOf(50))
        assertThat(position.getMoneyValues(Position.In.TRADE).realisedGain)
            .isEqualTo(BigDecimal("1000.00"))
    }

    private val costBasis = BigDecimal("2100.23")

    private val costBasisField = pCostBasis

    val gainValue = BigDecimal("211.56")

    private val realisedGainProp = "realisedGain"

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
        val position = accumulator.accumulate(buy, positions)
        buy = Trn(
            trnType = TrnType.BUY,
            asset = bidu,
            quantity = BigDecimal(2),
            tradeAmount = BigDecimal("405.21"),
        )
        accumulator.accumulate(buy, positions)
        val tradeMoney = position.getMoneyValues(Position.In.TRADE)
        assertThat(
            position.quantityValues.getTotal().multiply(tradeMoney.averageCost)
                .setScale(2, RoundingMode.HALF_UP),
        )
            .isEqualTo(tradeMoney.costBasis)
        val sell =
            Trn(
                trnType = TrnType.SELL,
                asset = bidu,
                quantity = BigDecimal(-3),
                tradeAmount = BigDecimal("841.63"),
            )
        accumulator.accumulate(sell, positions)
        assertThat(
            position.quantityValues.getTotal()
                .multiply(tradeMoney.averageCost).setScale(2, RoundingMode.HALF_UP),
        )
            .isEqualTo(tradeMoney.costValue)
        assertThat(tradeMoney)
            .hasFieldOrPropertyWithValue(costBasisField, costBasis)
            .hasFieldOrPropertyWithValue(pSales, sell.tradeAmount)
            .hasFieldOrPropertyWithValue(realisedGainProp, gainValue)
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
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(pSales, BigDecimal("2712.64"))
            .hasFieldOrPropertyWithValue(realisedGainProp, BigDecimal("612.41"))
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)
    }

    private val averageCostProp = "averageCost"

    @Test
    fun is_RealisedGainAfterSellingToZeroCalculated() {
        val positions = Positions(getPortfolio())
        val position = accumulator.accumulate(
            Trn(
                trnType = TrnType.BUY,
                asset = microsoft,
                quantity = BigDecimal(8),
                tradeAmount = BigDecimal("1695.02"),
            ),
            positions,
        )
        accumulator.accumulate(
            Trn(
                trnType = TrnType.BUY,
                asset = microsoft,
                quantity = BigDecimal(2),
                tradeAmount = BigDecimal("405.21"),
            ),
            positions,
        )
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal.TEN)
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(costBasisField, costBasis)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("210.023"))
            .hasFieldOrPropertyWithValue(realisedGainProp, BigDecimal.ZERO)
        var sell = Trn(trnType = TrnType.SELL, asset = microsoft, quantity = BigDecimal(3))
        sell.tradeAmount = BigDecimal("841.63")
        accumulator.accumulate(sell, positions)

        // Sell does not affect the cost basis or average cost, but it will create a signed gain
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(costBasisField, costBasis)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("210.023"))
            .hasFieldOrPropertyWithValue(realisedGainProp, gainValue)
        sell = Trn(trnType = TrnType.SELL, asset = microsoft, quantity = BigDecimal(7))
        sell.tradeAmount = BigDecimal("1871.01")
        accumulator.accumulate(sell, positions)

        // Sell down to 0; reset cost basis
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(realisedGainProp, BigDecimal("612.41"))
    }

    @Test
    fun is_RealisedGainAfterReenteringAPositionCalculated() {
        val positions = Positions(getPortfolio())
        val position = positions[intel]
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
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal.ZERO)
        val realisedGain = BigDecimal("-372.18")
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(realisedGainProp, realisedGain)

        // Re-enter the position
        val buy =
            Trn(trnType = TrnType.BUY, asset = intel, quantity = BigDecimal(60), tradeAmount = BigDecimal("1603.32"))
        accumulator.accumulate(buy, positions)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, buy.quantity)
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(costBasisField, buy.tradeAmount)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("26.722"))
            .hasFieldOrPropertyWithValue(realisedGainProp, realisedGain)

        // Second sell taking us back to zero. Verify that the accumulated gains.
        val tradeMoney = position.getMoneyValues(Position.In.TRADE)
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
        assertThat(position.getMoneyValues(Position.In.TRADE))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(realisedGainProp, previousGain.add(BigDecimal("60.99")))
    }
}
