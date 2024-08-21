package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.PortfolioUtils
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.PROP_COST_BASIS
import com.beancounter.position.Constants.Companion.PROP_REALIZED_GAIN
import com.beancounter.position.Constants.Companion.PROP_SALES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Test scenarios for Realised Gains.
 */
@SpringBootTest(classes = [Accumulator::class])
class RealisedGains {
    @Autowired
    private lateinit var accumulator: Accumulator

    private lateinit var microsoft: Asset
    private lateinit var intel: Asset
    private lateinit var bidu: Asset
    private lateinit var positions: Positions

    @BeforeEach
    fun setup() {
        microsoft = AssetUtils.getTestAsset(Constants.NASDAQ, "MSFT")
        intel = AssetUtils.getTestAsset(Constants.NASDAQ, "INTC")
        bidu = AssetUtils.getTestAsset(Constants.NASDAQ, "BIDU")
        positions = Positions(PortfolioUtils.getPortfolio())
    }

    @Test
    fun `Realised gain with signed quantities is calculated correctly`() {
        val positions = Positions()

        // Accumulate multiple BUY transactions
        accumulateMultipleBuys(positions)

        // Calculate and check the cost basis correctness after all BUY transactions
        val position = positions.getOrCreate(bidu) // Assume a getter for the position by asset
        val tradeMoney = position.getMoneyValues(Position.In.TRADE)
        assertCostBasisCorrectness(position, tradeMoney)

        // Process and verify the SELL transactions
        processAndVerifySellTransactions(positions, position, tradeMoney)
    }

    private fun accumulateMultipleBuys(positions: Positions) {
        val buys =
            listOf(
                Trn(trnType = TrnType.BUY, asset = bidu, quantity = BigDecimal(8), tradeAmount = BigDecimal("1695.02")),
                Trn(trnType = TrnType.BUY, asset = bidu, quantity = BigDecimal(2), tradeAmount = BigDecimal("405.21")),
            )
        buys.forEach { buy -> accumulator.accumulate(buy, positions) }
    }

    private fun assertCostBasisCorrectness(
        position: Position,
        tradeMoney: MoneyValues,
    ) {
        val calculatedCostBasis =
            position.quantityValues
                .getTotal()
                .multiply(tradeMoney.averageCost)
                .setScale(2, RoundingMode.HALF_UP)
        assertThat(calculatedCostBasis).isEqualTo(tradeMoney.costBasis)
    }

    private fun processAndVerifySellTransactions(
        positions: Positions,
        position: Position,
        tradeMoney: MoneyValues,
    ) {
        val sells =
            listOf(
                Trn(
                    trnType = TrnType.SELL,
                    asset = bidu,
                    quantity = BigDecimal(-3),
                    tradeAmount = BigDecimal("841.63"),
                ),
                Trn(
                    trnType = TrnType.SELL,
                    asset = bidu,
                    quantity = BigDecimal(-7),
                    tradeAmount = BigDecimal("1871.01"),
                ),
            )
        sells.forEach { sell ->
            accumulator.accumulate(sell, positions)
            // Specific assertions can be added here if needed for each sell
        }

        assertThat(tradeMoney)
            .hasFieldOrPropertyWithValue(PROP_COST_BASIS, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(PROP_SALES, BigDecimal("2712.64"))
            .hasFieldOrPropertyWithValue(PROP_REALIZED_GAIN, BigDecimal("612.41"))

        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)
    }
}
