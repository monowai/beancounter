package com.beancounter.position.valuation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.IsoCurrencyPair.Companion.toPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.PriceData.Companion.of
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.common.utils.MathUtils.Companion.nullSafe
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.accumulation.AccumulationStrategy
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.collections.set

/**
 * FX Related Market Value tests.
 */
internal class MarketValueTest {
    private val tenThousand = BigDecimal("10000.00")
    private val oneThousand = BigDecimal("1000.00")
    private val fiveThousand = BigDecimal("5000.00")
    private val twoThousand = BigDecimal("2000.00")
    private val gainOnDay = BigDecimal("500.00")
    private val oneHundred = BigDecimal("100.00")

    private val thousandShort = BigDecimal("-1000.00")

    private val twenty = BigDecimal("20.00")

    @Test
    fun is_MarketValueFromBehaviour() {
        val asset = getAsset(NASDAQ, "ABC")
        val simpleRate = BigDecimal("0.20")
        val buyTrn = Trn(trnType = TrnType.BUY, asset = asset, quantity = hundred)
        buyTrn.tradeAmount = twoThousand
        buyTrn.tradePortfolioRate = simpleRate
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        val positions = Positions(getPortfolio())
        val position = buyBehaviour.accumulate(buyTrn, positions)
        val marketData = MarketData(asset, close = BigDecimal("10.00"))
        marketData.previousClose = BigDecimal("5.00")

        // Revalue based on marketData prices
        val targetValues = MoneyValues(USD)
        targetValues.priceData = of(marketData)
        targetValues.averageCost = twenty
        targetValues.purchases = buyTrn.tradeAmount
        targetValues.costBasis = buyTrn.tradeAmount
        targetValues.costValue = buyTrn.tradeAmount
        targetValues.totalGain = thousandShort
        targetValues.unrealisedGain = thousandShort
        targetValues.gainOnDay = gainOnDay
        targetValues.marketValue = multiplyAbs(buyTrn.quantity, marketData.close)
        val fxRateMap = getRates(positions.portfolio, asset, simpleRate)
        MarketValue(Gains()).value(positions, marketData, fxRateMap)
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .usingRecursiveComparison()
            .isEqualTo(targetValues)
        val baseValues = MoneyValues(USD)
        baseValues.averageCost = twenty
        baseValues.priceData = of(marketData)
        baseValues.purchases = buyTrn.tradeAmount
        baseValues.costBasis = buyTrn.tradeAmount
        baseValues.costValue = buyTrn.tradeAmount
        baseValues.totalGain = thousandShort
        baseValues.unrealisedGain = thousandShort
        baseValues.gainOnDay = gainOnDay
        baseValues.marketValue = nullSafe(multiplyAbs(buyTrn.quantity, marketData.close))
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base))
            .usingRecursiveComparison()
            .isEqualTo(baseValues)
        val pfValues = MoneyValues(positions.portfolio.currency)
        pfValues.costBasis = tenThousand
        pfValues.purchases = tenThousand
        pfValues.priceData = of(marketData, simpleRate)
        pfValues.marketValue = BigDecimal("200.00")
        pfValues.averageCost = oneHundred
        pfValues.costValue = tenThousand
        val gain = BigDecimal("-9800.00")
        pfValues.unrealisedGain = gain
        pfValues.totalGain = gain
        pfValues.gainOnDay = oneHundred
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
            .usingRecursiveComparison()
            .isEqualTo(pfValues)
    }

    @Test
    fun is_GainsOnSellBehaviour() {
        val portfolio = getPortfolio()
        val asset = getAsset(NASDAQ, "ABC")
        val fxRateMap: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val simpleRate = BigDecimal("0.20")
        val pair = toPair(
            portfolio.currency,
            asset.market.currency
        )
        if (pair != null) {
            fxRateMap[pair] = FxRate(
                Currency("X"),
                Currency("X"),
                simpleRate,
                null
            )
        }
        val buyTrn = Trn(
            trnType = TrnType.BUY,
            asset = asset,
            quantity = hundred,
            tradeAmount = twoThousand,
            tradePortfolioRate = simpleRate
        )
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        val positions = Positions(portfolio)
        val position = buyBehaviour.accumulate(buyTrn, positions)
        val sellTrn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal("3000.00"),
                tradePortfolioRate = simpleRate
            )
        val sellBehaviour: AccumulationStrategy = SellBehaviour()
        sellBehaviour.accumulate(sellTrn, positions)
        val marketData = MarketData(asset, close = BigDecimal("10.00"))
        marketData.previousClose = BigDecimal("9.00")
        MarketValue(Gains()).value(positions, marketData, fxRateMap)
        val usdValues = MoneyValues(USD)
        usdValues.marketValue = BigDecimal("0")
        usdValues.averageCost = BigDecimal.ZERO
        usdValues.priceData = of(marketData, BigDecimal.ONE)
        usdValues.purchases = buyTrn.tradeAmount
        usdValues.sales = sellTrn.tradeAmount
        usdValues.costValue = BigDecimal.ZERO
        usdValues.realisedGain = oneThousand
        usdValues.unrealisedGain = BigDecimal.ZERO
        usdValues.totalGain = oneThousand
        assertThat(position.getMoneyValues(Position.In.TRADE, position.asset.market.currency))
            .usingRecursiveComparison()
            .isEqualTo(usdValues)
        assertThat(position.getMoneyValues(Position.In.BASE, positions.portfolio.base))
            .usingRecursiveComparison()
            .isEqualTo(usdValues)
        val pfValues = MoneyValues(portfolio.currency)
        pfValues.marketValue = BigDecimal("0")
        pfValues.averageCost = BigDecimal.ZERO
        pfValues.priceData = of(marketData, simpleRate)
        pfValues.purchases = divide(buyTrn.tradeAmount, simpleRate)
        pfValues.costValue = BigDecimal.ZERO
        pfValues.sales = divide(sellTrn.tradeAmount, simpleRate)
        pfValues.realisedGain = fiveThousand
        pfValues.unrealisedGain = BigDecimal.ZERO
        pfValues.totalGain = fiveThousand
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, positions.portfolio.currency))
            .usingRecursiveComparison()
            .isEqualTo(pfValues)
    }

    @Test
    fun is_MarketValueWithNoPriceComputedForBuyBehaviour() {
        val asset = getAsset(NASDAQ, "ABC")
        val simpleRate = BigDecimal("0.20")
        val buyTrn = Trn(trnType = TrnType.BUY, asset = asset, quantity = hundred, tradeAmount = BigDecimal("2000.00"))
        assertThat(buyTrn.tradeCurrency.code).isEqualTo(asset.market.currency.code)
        buyTrn.tradePortfolioRate = simpleRate
        val buyBehaviour: AccumulationStrategy = BuyBehaviour()
        // Behaviour tests manage their own Positions.
        val position = Position(asset)
        val portfolio = getPortfolio()
        val positions = Positions(portfolio)
        buyBehaviour.accumulate(buyTrn, positions)
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
        simpleRate: BigDecimal
    ): Map<IsoCurrencyPair, FxRate> {
        val fxRateMap: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val pair = toPair(
            portfolio.currency,
            asset.market.currency
        )
        if (pair != null) {
            fxRateMap[pair] = FxRate(
                Currency("test"),
                Currency("TEST"),
                simpleRate,
                null
            )
        }
        return fxRateMap
    }
}
