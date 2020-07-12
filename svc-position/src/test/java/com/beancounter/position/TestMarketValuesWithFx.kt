package com.beancounter.position

import com.beancounter.common.model.*
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.PriceData.Companion.of
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.common.utils.MathUtils.Companion.nullSafe
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.accumulation.AccumulationStrategy
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import com.beancounter.position.valuation.Gains
import com.beancounter.position.valuation.MarketValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

internal class TestMarketValuesWithFx {
    @Test
    fun is_MarketValue() {
        val asset = getAsset("Test", "ABC")
        val simpleRate = BigDecimal("0.20")
        val buyTrn = Trn(TrnType.BUY, asset, BigDecimal(100))
        buyTrn.tradeAmount = BigDecimal("2000.00")
        buyTrn.tradePortfolioRate = simpleRate
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        val position = Position(asset)
        val portfolio = getPortfolio("MV")
        buyBehaviour.accumulate(buyTrn, portfolio, position)
        val positions = Positions(portfolio)
        positions.add(position)
        val marketData = MarketData(asset)
        marketData.close = BigDecimal("10.00")
        marketData.previousClose = BigDecimal("5.00")


        // Revalue based on marketData prices
        val targetValues = MoneyValues(Currency("USD"))
        targetValues.priceData = of(marketData)
        targetValues.averageCost = BigDecimal("20.00")
        targetValues.purchases = buyTrn.tradeAmount
        targetValues.costBasis = buyTrn.tradeAmount
        targetValues.costValue = buyTrn.tradeAmount
        targetValues.totalGain = BigDecimal("-1000.00")
        targetValues.unrealisedGain = BigDecimal("-1000.00")
        targetValues.marketValue = Objects.requireNonNull(
                multiply(buyTrn.quantity, marketData.close))!!
        val fxRateMap = getRates(portfolio, asset, simpleRate)
        MarketValue(Gains()).value(positions, marketData, fxRateMap)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
                .isEqualToIgnoringGivenFields(targetValues, "priceData", "portfolio")
        val baseValues = MoneyValues(Currency("USD"))
        baseValues.averageCost = BigDecimal("20.00")
        baseValues.priceData = of(marketData)
        baseValues.purchases = buyTrn.tradeAmount
        baseValues.costBasis = buyTrn.tradeAmount
        baseValues.costValue = buyTrn.tradeAmount
        baseValues.totalGain = BigDecimal("-1000.00")
        baseValues.unrealisedGain = BigDecimal("-1000.00")
        baseValues.marketValue = nullSafe(multiply(buyTrn.quantity, marketData.close))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base))
                .isEqualToIgnoringGivenFields(baseValues, "priceData", "portfolio")
        val pfValues = MoneyValues(portfolio.currency)
        pfValues.costBasis = BigDecimal("10000.00")
        pfValues.purchases = BigDecimal("10000.00")
        pfValues.priceData = of(marketData, simpleRate)
        pfValues.marketValue = BigDecimal("200.00")
        pfValues.averageCost = BigDecimal("100.00")
        pfValues.costValue = BigDecimal("10000.00")
        pfValues.unrealisedGain = BigDecimal("-9800.00")
        pfValues.totalGain = BigDecimal("-9800.00")
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
                .isEqualToIgnoringGivenFields(pfValues, "priceData")
    }

    @Test
    fun is_GainsOnSell() {
        val portfolio = getPortfolio("MV")
        val asset = getAsset("Test", "ABC")
        val fxRateMap: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val simpleRate = BigDecimal("0.20")
        val pair = toPair(
                portfolio.currency,
                asset.market.currency)
        if (pair != null) {
            fxRateMap[pair] = FxRate(
                    Currency("X"), Currency("X"),
                    simpleRate, null)
        }
        val buyTrn = Trn(TrnType.BUY, asset, BigDecimal(100))
        buyTrn.tradeAmount = BigDecimal("2000.00")
        buyTrn.tradePortfolioRate = simpleRate
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        val position = Position(asset)
        buyBehaviour.accumulate(buyTrn, portfolio, position)
        val positions = Positions(portfolio)
        positions.add(position)
        val sellTrn = Trn(TrnType.SELL, asset, BigDecimal(100))
        sellTrn.tradeAmount = BigDecimal("3000.00")
        sellTrn.tradePortfolioRate = simpleRate
        val sellBehaviour: AccumulationStrategy = SellBehaviour()
        sellBehaviour.accumulate(sellTrn, portfolio, position)
        val marketData = MarketData(asset)
        marketData.close = BigDecimal("10.00")
        marketData.previousClose = BigDecimal("9.00")
        MarketValue(Gains()).value(positions, marketData, fxRateMap)
        val usdValues = MoneyValues(Currency("USD"))
        usdValues.marketValue = BigDecimal("0")
        usdValues.averageCost = BigDecimal.ZERO
        usdValues.priceData = of(marketData, BigDecimal.ONE)
        usdValues.purchases = buyTrn.tradeAmount
        usdValues.sales = sellTrn.tradeAmount
        usdValues.costValue = BigDecimal.ZERO
        usdValues.realisedGain = BigDecimal("1000.00")
        usdValues.unrealisedGain = BigDecimal.ZERO
        usdValues.totalGain = BigDecimal("1000.00")
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
                .isEqualToIgnoringGivenFields(usdValues, "priceData", "portfolio")
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base))
                .isEqualToIgnoringGivenFields(usdValues, "priceData", "portfolio")
        val pfValues = MoneyValues(portfolio.currency)
        pfValues.marketValue = BigDecimal("0")
        pfValues.averageCost = BigDecimal.ZERO
        pfValues.priceData = of(marketData, simpleRate)
        pfValues.purchases = Objects.requireNonNull(divide(buyTrn.tradeAmount, simpleRate))!!
        pfValues.costValue = BigDecimal.ZERO
        pfValues.sales = divide(sellTrn.tradeAmount, simpleRate)!!
        pfValues.realisedGain = BigDecimal("5000.00")
        pfValues.unrealisedGain = BigDecimal.ZERO
        pfValues.totalGain = BigDecimal("5000.00")
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
                .isEqualToIgnoringGivenFields(pfValues, "priceData", "portfolio")
    }

    @Test
    fun is_MarketValueWithNoPriceComputed() {
        val asset = getAsset("Test", "ABC")
        val simpleRate = BigDecimal("0.20")
        val buyTrn = Trn(TrnType.BUY, asset, BigDecimal(100))
        buyTrn.tradeAmount = BigDecimal("2000.00")
        assertThat(buyTrn.tradeCurrency.code).isEqualTo(asset.market.currency.code)
        buyTrn.tradePortfolioRate = simpleRate
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        val position = Position(asset)
        val portfolio = getPortfolio("MV")
        buyBehaviour.accumulate(buyTrn, portfolio, position)
        val positions = Positions(portfolio)
        positions.add(position)
        val marketData = MarketData(asset)
        val fxRateMap = getRates(portfolio, asset, simpleRate)

        // Revalue based on No Market data
        val result = MarketValue(Gains()).value(positions, marketData, fxRateMap)
        assertThat(result).isNotNull.hasFieldOrProperty("moneyValues")
    }

    private fun getRates(
            portfolio: Portfolio,
            asset: Asset,
            simpleRate: BigDecimal): Map<IsoCurrencyPair, FxRate> {
        val fxRateMap: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val pair = toPair(
                portfolio.currency,
                asset.market.currency)
        if (pair != null){
            fxRateMap[pair] = FxRate(Currency("test"), Currency("TEST"),
                    simpleRate, null)
        }
        return fxRateMap
    }
}