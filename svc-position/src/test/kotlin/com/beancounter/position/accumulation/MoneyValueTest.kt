package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.fourK
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.ten
import com.beancounter.position.Constants.Companion.twenty
import com.beancounter.position.Constants.Companion.twoK
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * assert money values functionality.
 */
@SpringBootTest(classes = [Accumulator::class])
internal class MoneyValueTest {
    private val microsoft = getAsset(NASDAQ, "MSFT")
    private val intel = getAsset(NASDAQ, "INTC")
    private val bidu = getAsset(NASDAQ, "BIDU")
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var accumulator: Accumulator

    private val tradePortfolioRate = BigDecimal("100.00")

    /**
     * Tests the lifecycle of a transaction over all supported transaction types and verifies
     * key values in the various currency buckets.
     *
     * Simple FX values make assertions easier to calculate.
     */
    @Test
    @Throws(IOException::class)
    fun is_ValuedInTrackedCurrencies() {
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = microsoft,
                quantity = hundred,
                tradeAmount = twoK,
                tradeCashRate = ten,
                tradeBaseRate = BigDecimal("1.00"),
                tradePortfolioRate = tradePortfolioRate,
            )
        val buyBehaviour = BuyBehaviour()
        val positions = Positions()
        val position = positions[microsoft]
        buyBehaviour.accumulate(buyTrn, positions)
        assertThat(position.quantityValues.getTotal())
            .isEqualTo(hundred)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).purchases)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).costBasis)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).purchases)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).costBasis)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).costBasis)
            .isEqualTo(twenty)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).purchases)
            .isEqualTo(twenty)
        val diviTrn = Trn(
            trnType = TrnType.DIVI,
            asset = microsoft,
            quantity = BigDecimal.ZERO,
            tradeAmount = BigDecimal.TEN,
            cashAmount = BigDecimal.TEN,
            tradeBaseRate = BigDecimal.ONE,
            tradeCashRate = BigDecimal.TEN,
            tradePortfolioRate = tradePortfolioRate,
        )
        val dividendBehaviour = DividendBehaviour()
        dividendBehaviour.accumulate(diviTrn, positions)
        assertThat(position.quantityValues.getTotal())
            .isEqualTo(hundred)
        val dividends = "dividends"
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(dividends, ten)
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base))
            .hasFieldOrPropertyWithValue(dividends, ten)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
            .hasFieldOrPropertyWithValue(dividends, BigDecimal(".10"))
        val dateUtils = DateUtils()
        assertThat(position.dateValues.lastDividend).isNotNull
        assertThat(
            dateUtils.isToday(
                position.dateValues.lastDividend!!.toString(),
            ),
        )
        val splitTrn = Trn(
            trnType = TrnType.SPLIT,
            asset = microsoft,
            quantity = BigDecimal.TEN,
            cashAmount = BigDecimal.TEN,
            tradeBaseRate = BigDecimal.ONE,
            tradeCashRate = BigDecimal.TEN,
            tradePortfolioRate = tradePortfolioRate,
        )
        SplitBehaviour().accumulate(splitTrn, positions)
        var moneyValues = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        assertThat(moneyValues).isNotNull
        val bytes = objectMapper.writeValueAsBytes(position)
        val deepCopy = objectMapper.readValue(bytes, Position::class.java)
        assertThat(moneyValues.costBasis)
            .isEqualTo(deepCopy.getMoneyValues(Position.In.TRADE, position.asset.market.currency).costBasis)
        moneyValues = position.getMoneyValues(Position.In.BASE, positions.portfolio.base)
        assertThat(moneyValues.costBasis)
            .isEqualTo(deepCopy.getMoneyValues(Position.In.BASE, positions.portfolio.base).costBasis)
        moneyValues = position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency)
        assertThat(moneyValues.costBasis)
            .isEqualTo(deepCopy.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).costBasis)
        val sellTrn = Trn(
            trnType = TrnType.SELL,
            asset = microsoft,
            quantity = position.quantityValues.getTotal(),
            tradeAmount = fourK,
            tradeBaseRate = BigDecimal.ONE,
            tradeCashRate = BigDecimal.TEN,
            tradePortfolioRate = tradePortfolioRate,
        )
        val sellBehaviour = SellBehaviour()
        sellBehaviour.accumulate(sellTrn, positions)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).sales)
            .isEqualTo(fourK)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).realisedGain)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).sales)
            .isEqualTo(fourK)
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).realisedGain)
            .isEqualTo(twoK)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).sales)
            .isEqualTo(BigDecimal("40.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).realisedGain)
            .isEqualTo(twenty)
    }

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
        val sell =
            Trn(trnType = TrnType.SELL, asset = microsoft, quantity = BigDecimal(50), tradeAmount = BigDecimal(2000))
        val position = accumulator.accumulate(sell, positions)
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.valueOf(50))
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).realisedGain)
            .isEqualTo(BigDecimal("1000.00"))
    }

    private val costBasis = BigDecimal("2100.23")

    private val costBasisField = "costBasis"

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
        val tradeMoney = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        assertThat(
            position.quantityValues.getTotal().multiply(tradeMoney.averageCost)
                .setScale(2, RoundingMode.HALF_UP),
        )
            .isEqualTo(tradeMoney.costBasis)
        var sell =
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
            .hasFieldOrPropertyWithValue("sales", sell.tradeAmount)
            .hasFieldOrPropertyWithValue(realisedGainProp, gainValue)
        sell = Trn(
            trnType = TrnType.SELL,
            asset = bidu,
            quantity = BigDecimal(-7),
            tradeAmount = BigDecimal("1871.01"),
        )
        accumulator.accumulate(sell, positions)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("sales", BigDecimal("2712.64"))
            .hasFieldOrPropertyWithValue(realisedGainProp, BigDecimal("612.41"))
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)
    }

    private val averageCostProp = "averageCost"

    @Test
    fun is_RealisedGainAfterSellingToZeroCalculated() {
        val positions = Positions(getPortfolio())
        var buy =
            Trn(trnType = TrnType.BUY, asset = microsoft, quantity = BigDecimal(8), tradeAmount = BigDecimal("1695.02"))
        val position = accumulator.accumulate(buy, positions)
        buy =
            Trn(trnType = TrnType.BUY, asset = microsoft, quantity = BigDecimal(2), tradeAmount = BigDecimal("405.21"))
        accumulator.accumulate(buy, positions)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal.TEN)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, costBasis)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("210.023"))
            .hasFieldOrPropertyWithValue(realisedGainProp, BigDecimal.ZERO)
        var sell = Trn(trnType = TrnType.SELL, asset = microsoft, quantity = BigDecimal(3))
        sell.tradeAmount = BigDecimal("841.63")
        accumulator.accumulate(sell, positions)

        // Sell does not affect the cost basis or average cost, but it will create a signed gain
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, costBasis)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("210.023"))
            .hasFieldOrPropertyWithValue(realisedGainProp, gainValue)
        sell = Trn(trnType = TrnType.SELL, asset = microsoft, quantity = BigDecimal(7))
        sell.tradeAmount = BigDecimal("1871.01")
        accumulator.accumulate(sell, positions)

        // Sell down to 0; reset cost basis
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal("0"))
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("0"))
            .hasFieldOrPropertyWithValue(realisedGainProp, BigDecimal("612.41"))
    }

    @Test
    fun is_RealisedGainAfterReenteringAPositionCalculated() {
        val positions = Positions(getPortfolio())
        val position = positions[intel]
        var buy = Trn(trnType = TrnType.BUY, asset = intel, quantity = BigDecimal(80))
        buy.tradeAmount = BigDecimal("2646.08")
        accumulator.accumulate(buy, positions)
        var sell = Trn(trnType = TrnType.SELL, asset = intel, quantity = BigDecimal(80))
        sell.tradeAmount = BigDecimal("2273.9")
        accumulator.accumulate(sell, positions)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, BigDecimal.ZERO)
        val realisedGain = BigDecimal("-372.18")
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(realisedGainProp, realisedGain)

        // Re-enter the position
        buy = Trn(trnType = TrnType.BUY, asset = intel, quantity = BigDecimal(60))
        buy.tradeAmount = BigDecimal("1603.32")
        accumulator.accumulate(buy, positions)
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue(totalField, buy.quantity)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, buy.tradeAmount)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal("26.722"))
            .hasFieldOrPropertyWithValue(realisedGainProp, realisedGain)

        // Second sell taking us back to zero. Verify that the accumulated gains.
        sell = Trn(trnType = TrnType.SELL, asset = intel, quantity = BigDecimal(60))
        sell.tradeAmount = BigDecimal("1664.31")
        val tradeMoney = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        assertThat(tradeMoney).isNotNull
        val previousGain = tradeMoney.realisedGain // Track the previous gain
        accumulator.accumulate(sell, positions)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue(costBasisField, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(averageCostProp, BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue(realisedGainProp, previousGain.add(BigDecimal("60.99")))
    }
}
