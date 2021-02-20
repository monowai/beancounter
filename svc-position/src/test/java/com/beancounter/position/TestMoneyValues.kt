package com.beancounter.position

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.DividendBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.accumulation.SplitBehaviour
import com.beancounter.position.service.Accumulator
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

@SpringBootTest(classes = [Accumulator::class])
internal class TestMoneyValues {
    private val microsoft = getAsset("NYSE", "MSFT")
    private val intel = getAsset("NYSE", "INTC")
    private val bidu = getAsset("NYSE", "BIDU")
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var accumulator: Accumulator

    /**
     * Tests the lifecycle of a transaction over all supported transaction types and verifies
     * key values in the various currency buckets.
     *
     *
     * Possibly the most important unit test in the suite.
     *
     *
     * Simple FX values make assertions easier to calculate.
     */
    @Test
    @Throws(IOException::class)
    fun is_ValuedInTrackedCurrencies() {
        val buyTrn = Trn(TrnType.BUY, microsoft, BigDecimal(100))
        buyTrn.tradeAmount = BigDecimal("2000.00")
        buyTrn.tradeBaseRate = BigDecimal("1.00")
        buyTrn.tradeCashRate = BigDecimal("10.00")
        buyTrn.tradePortfolioRate = TRADE_PORTFOLIO_RATE
        val buyBehaviour = BuyBehaviour()
        val positions = Positions(getPortfolio("TEST"))
        val position = positions[microsoft]
        buyBehaviour.accumulate(buyTrn, positions.portfolio, position)
        assertThat(position.quantityValues.getTotal())
            .isEqualTo(BigDecimal("100"))
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).purchases)
            .isEqualTo(BigDecimal("2000.00"))
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).costBasis)
            .isEqualTo(BigDecimal("2000.00"))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).purchases)
            .isEqualTo(BigDecimal("2000.00"))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).costBasis)
            .isEqualTo(BigDecimal("2000.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).costBasis)
            .isEqualTo(BigDecimal("20.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).purchases)
            .isEqualTo(BigDecimal("20.00"))
        val diviTrn = Trn(TrnType.DIVI, microsoft, BigDecimal.ZERO)
        diviTrn.tradeAmount = BigDecimal.TEN
        diviTrn.cashAmount = BigDecimal.TEN
        diviTrn.tradeBaseRate = BigDecimal.ONE
        diviTrn.tradeCashRate = BigDecimal.TEN
        diviTrn.tradePortfolioRate = TRADE_PORTFOLIO_RATE
        val dividendBehaviour = DividendBehaviour()
        dividendBehaviour.accumulate(diviTrn, positions.portfolio, position)
        assertThat(position.quantityValues.getTotal())
            .isEqualTo(BigDecimal("100"))
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("dividends", BigDecimal("10.00"))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base))
            .hasFieldOrPropertyWithValue("dividends", BigDecimal("10.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
            .hasFieldOrPropertyWithValue("dividends", BigDecimal(".10"))
        val dateUtils = DateUtils()
        assertThat(
            dateUtils.isToday(
                dateUtils.getDateString(position.dateValues.lastDividend)
            )
        )
        val splitTrn = Trn(TrnType.SPLIT, microsoft, BigDecimal.TEN)
        splitTrn.cashAmount = BigDecimal.TEN
        splitTrn.tradeBaseRate = BigDecimal.ONE
        splitTrn.tradeCashRate = BigDecimal.TEN
        splitTrn.tradePortfolioRate = TRADE_PORTFOLIO_RATE
        SplitBehaviour().accumulate(splitTrn, positions.portfolio, position)
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
        val sellTrn = Trn(TrnType.SELL, microsoft, position.quantityValues.getTotal())
        sellTrn.tradeAmount = BigDecimal("4000.00")
        sellTrn.tradeBaseRate = BigDecimal.ONE
        sellTrn.tradeCashRate = BigDecimal.TEN
        sellTrn.tradePortfolioRate = TRADE_PORTFOLIO_RATE
        val sellBehaviour = SellBehaviour()
        sellBehaviour.accumulate(sellTrn, positions.portfolio, position)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).sales)
            .isEqualTo(BigDecimal("4000.00"))
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).realisedGain)
            .isEqualTo(BigDecimal("2000.00"))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).sales)
            .isEqualTo(BigDecimal("4000.00"))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base).realisedGain)
            .isEqualTo(BigDecimal("2000.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).sales)
            .isEqualTo(BigDecimal("40.00"))
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency).realisedGain)
            .isEqualTo(BigDecimal("20.00"))
    }

    @Test
    fun is_QuantityAndMarketValueCalculated() {
        val positions = Positions(getPortfolio("TEST"))
        var position = positions[microsoft]
        assertThat(position)
            .isNotNull
        val buy = Trn(TrnType.BUY, microsoft, BigDecimal(100))
        buy.tradeAmount = BigDecimal(2000)
        val portfolio = getPortfolio("TEST")
        position = accumulator.accumulate(buy, portfolio, position)
        positions.add(position)
        position = positions[microsoft]
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("purchased", BigDecimal(100))
            .hasFieldOrPropertyWithValue("total", BigDecimal(100))
    }

    @Test
    fun is_RealisedGainCalculated() {
        val positions = Positions(getPortfolio("TEST"))
        var position = positions[microsoft]
        assertThat(position)
            .isNotNull
        val buy = Trn(TrnType.BUY, microsoft, BigDecimal(100))
        buy.tradeAmount = BigDecimal(2000)
        position = accumulator.accumulate(buy, positions.portfolio, position)
        positions.add(position)
        position = positions[microsoft]
        val sell = Trn(TrnType.SELL, microsoft, BigDecimal(50))
        sell.tradeAmount = BigDecimal(2000)
        position = accumulator.accumulate(sell, positions.portfolio, position)
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.valueOf(50))
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency).realisedGain)
            .isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun is_RealisedGainWithSignedQuantitiesCalculated() {
        val positions = Positions(getPortfolio("TEST"))
        var position = positions[bidu]
        assertThat(position)
            .isNotNull
        var buy = Trn(TrnType.BUY, bidu, BigDecimal(8))
        buy.tradeAmount = BigDecimal("1695.02")
        val portfolio = getPortfolio("TEST")
        position = accumulator.accumulate(buy, portfolio, position)
        positions.add(position)
        position = positions[bidu]
        buy = Trn(TrnType.BUY, bidu, BigDecimal(2))
        buy.tradeAmount = BigDecimal("405.21")
        position = accumulator.accumulate(buy, portfolio, position)
        val tradeMoney = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        assertThat(
            position.quantityValues.getTotal().multiply(tradeMoney.averageCost)
                .setScale(2, RoundingMode.HALF_UP)
        )
            .isEqualTo(tradeMoney.costBasis)
        var sell = Trn(TrnType.SELL, bidu, BigDecimal(-3))
        sell.tradeAmount = BigDecimal("841.63")
        position = accumulator.accumulate(sell, portfolio, position)
        assertThat(
            position.quantityValues.getTotal()
                .multiply(tradeMoney.averageCost).setScale(2, RoundingMode.HALF_UP)
        )
            .isEqualTo(tradeMoney.costValue)
        assertThat(tradeMoney)
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("2100.23"))
            .hasFieldOrPropertyWithValue("sales", BigDecimal("841.63"))
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal("211.56"))
        sell = Trn(TrnType.SELL, bidu, BigDecimal(-7))
        sell.tradeAmount = BigDecimal("1871.01")
        position = accumulator.accumulate(sell, portfolio, position)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("sales", BigDecimal("2712.64"))
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal("612.41"))
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun is_RealisedGainAfterSellingToZeroCalculated() {
        val positions = Positions(getPortfolio("TEST"))
        var position = positions[microsoft]
        assertThat(position)
            .isNotNull
        var buy = Trn(TrnType.BUY, microsoft, BigDecimal(8))
        buy.tradeAmount = BigDecimal("1695.02")
        position = accumulator.accumulate(buy, positions.portfolio, position)
        positions.add(position)
        buy = Trn(TrnType.BUY, microsoft, BigDecimal(2))
        buy.tradeAmount = BigDecimal("405.21")
        accumulator.accumulate(buy, positions.portfolio, position)
        position = positions[microsoft]
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal.TEN)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("2100.23"))
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal("210.023"))
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal.ZERO)
        var sell = Trn(TrnType.SELL, microsoft, BigDecimal(3))
        sell.tradeAmount = BigDecimal("841.63")
        accumulator.accumulate(sell, positions.portfolio, position)

        // Sell does not affect the cost basis or average cost, but it will create a signed gain
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("2100.23"))
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal("210.023"))
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal("211.56"))
        sell = Trn(TrnType.SELL, microsoft, BigDecimal(7))
        sell.tradeAmount = BigDecimal("1871.01")
        accumulator.accumulate(sell, positions.portfolio, position)

        // Sell down to 0; reset cost basis
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal("0"))
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal("0"))
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal("612.41"))
    }

    @Test
    fun is_RealisedGainAfterReenteringAPositionCalculated() {
        val positions = Positions(getPortfolio("TEST"))
        var position = positions[intel]
        assertThat(position)
            .isNotNull
        var buy = Trn(TrnType.BUY, intel, BigDecimal(80))
        buy.tradeAmount = BigDecimal("2646.08")
        position = accumulator.accumulate(buy, positions.portfolio, position)
        positions.add(position)
        var sell = Trn(TrnType.SELL, intel, BigDecimal(80))
        sell.tradeAmount = BigDecimal("2273.9")
        accumulator.accumulate(sell, positions.portfolio, position)
        position = positions[intel]
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal.ZERO)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal("-372.18"))

        // Re-enter the position
        buy = Trn(TrnType.BUY, intel, BigDecimal(60))
        buy.tradeAmount = BigDecimal("1603.32")
        accumulator.accumulate(buy, positions.portfolio, position)
        position = positions[intel]
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("total", buy.quantity)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", buy.tradeAmount)
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal("26.722"))
            .hasFieldOrPropertyWithValue("realisedGain", BigDecimal("-372.18"))

        // Second sell taking us back to zero. Verify that the accumulated gains.
        sell = Trn(TrnType.SELL, intel, BigDecimal(60))
        sell.tradeAmount = BigDecimal("1664.31")
        val tradeMoney = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        assertThat(tradeMoney).isNotNull
        val previousGain = tradeMoney.realisedGain // Track the previous gain
        accumulator.accumulate(sell, positions.portfolio, position)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .hasFieldOrPropertyWithValue("costBasis", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("averageCost", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("realisedGain", previousGain.add(BigDecimal("60.99")))
    }

    companion object {
        private val TRADE_PORTFOLIO_RATE = BigDecimal("100.00")
    }
}
