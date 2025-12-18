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
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.common.utils.MathUtils.Companion.nullSafe
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.accumulation.AccumulationStrategy
import com.beancounter.position.accumulation.BuyBehaviour
import com.beancounter.position.accumulation.SellBehaviour
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * FX Related Market Value tests.
 */
internal class MarketValueTest {
    private val oneThousand = BigDecimal("1000.00")
    private val twoThousand = BigDecimal("2000.00")
    private val gainOnDay = BigDecimal("500.00")
    private val oneHundred = BigDecimal("100.00")

    private val thousandShort = BigDecimal("-1000.00")

    private val twenty = BigDecimal("20.00")
    private val fourH = BigDecimal("400.00")
    private val twoH = BigDecimal("200.00")
    private val buyBehaviour: AccumulationStrategy = BuyBehaviour()
    private val sellBehaviour: AccumulationStrategy = SellBehaviour()
    private val portfolio = getPortfolio()
    private val asset =
        getTestAsset(
            NASDAQ,
            "ABC"
        )
    private val simpleRate = BigDecimal("0.20")

    @Test
    fun `should calculate market value from behaviour`() {
        val positions = Positions(portfolio)
        val position =
            buyBehaviour.accumulate(
                Trn(
                    trnType = TrnType.BUY,
                    asset = asset,
                    quantity = hundred,
                    tradeAmount = twoThousand,
                    tradePortfolioRate = simpleRate,
                    portfolio = portfolio
                ),
                positions
            )
        val marketData =
            MarketData(
                asset,
                close = BigDecimal("10.00")
            )
        marketData.previousClose = BigDecimal("5.00")

        // Revalue based on marketData prices
        val targetValues = MoneyValues(USD)
        targetValues.priceData = of(marketData)
        targetValues.averageCost = twenty
        targetValues.purchases = twoThousand
        targetValues.costBasis = twoThousand
        targetValues.costValue = twoThousand
        targetValues.totalGain = thousandShort
        targetValues.unrealisedGain = thousandShort
        targetValues.gainOnDay = gainOnDay
        targetValues.marketValue =
            multiplyAbs(
                hundred,
                marketData.close
            )
        val fxRateMap =
            getRates(
                positions.portfolio,
                asset,
                simpleRate
            )
        MarketValue(Gains()).value(
            positions,
            marketData,
            fxRateMap
        )
        assertThat(
            position.getMoneyValues(
                Position.In.TRADE,
                position.asset.market.currency
            )
        ).usingRecursiveComparison()
            .isEqualTo(targetValues)
        val baseValues = MoneyValues(USD)
        baseValues.averageCost = twenty
        baseValues.priceData = of(marketData)
        baseValues.purchases = twoThousand
        baseValues.costBasis = twoThousand
        baseValues.costValue = twoThousand
        baseValues.totalGain = thousandShort
        baseValues.unrealisedGain = thousandShort
        baseValues.gainOnDay = gainOnDay
        baseValues.marketValue =
            nullSafe(
                multiplyAbs(
                    oneHundred,
                    marketData.close
                )
            )
        assertThat(
            position.getMoneyValues(
                Position.In.BASE,
                positions.portfolio.base
            )
        ).usingRecursiveComparison()
            .isEqualTo(baseValues)
        val pfValues = MoneyValues(positions.portfolio.currency)

        pfValues.costBasis = fourH
        pfValues.purchases = fourH
        pfValues.priceData =
            of(
                marketData,
                simpleRate
            )
        pfValues.marketValue = BigDecimal("200.00")
        pfValues.averageCost = BigDecimal("4.00")
        pfValues.costValue = fourH
        val gain = BigDecimal("-200.00")
        pfValues.unrealisedGain = gain
        pfValues.totalGain = gain
        pfValues.gainOnDay = oneHundred
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
            .usingRecursiveComparison()
            .isEqualTo(pfValues)
    }

    @Test
    fun `should calculate gains on sell behaviour`() {
        val simpleRate = BigDecimal("0.20")
        val fxRateMap =
            mapOf(
                Pair(
                    toPair(
                        asset.market.currency,
                        portfolio.currency
                    )!!,
                    FxRate(
                        Currency("X"),
                        Currency("X"),
                        simpleRate
                    )
                )
            )
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = hundred,
                tradeAmount = twoThousand,
                tradePortfolioRate = simpleRate,
                portfolio = portfolio
            )

        val positions = Positions(portfolio)
        val position =
            buyBehaviour.accumulate(
                buyTrn,
                positions
            )
        val sellTrn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal("3000.00"),
                tradePortfolioRate = simpleRate,
                portfolio = portfolio
            )
        sellBehaviour.accumulate(
            sellTrn,
            positions
        )
        val marketData =
            MarketData(
                asset,
                close = BigDecimal("10.00"),
                previousClose = BigDecimal("9.00")
            )
        MarketValue(Gains()).value(
            positions,
            marketData,
            fxRateMap
        )
        verifyTradeBaseMoneyValues(
            MoneyValues(USD),
            marketData,
            buyTrn,
            sellTrn,
            position
        )
        verifyPortfolioMoneyValues(
            MoneyValues(portfolio.currency),
            marketData,
            simpleRate,
            position
        )
    }

    private fun verifyTradeBaseMoneyValues(
        usdValues: MoneyValues,
        marketData: MarketData,
        buyTrn: Trn,
        sellTrn: Trn,
        position: Position
    ) {
        usdValues.marketValue = BigDecimal("0")
        usdValues.averageCost = BigDecimal.ZERO
        usdValues.priceData =
            of(
                marketData,
                BigDecimal.ONE
            )
        usdValues.purchases = buyTrn.tradeAmount
        usdValues.sales = sellTrn.tradeAmount
        usdValues.costValue = BigDecimal.ZERO
        usdValues.realisedGain = oneThousand
        usdValues.unrealisedGain = BigDecimal.ZERO
        usdValues.totalGain = oneThousand
        assertThat(
            position.getMoneyValues(
                Position.In.TRADE,
                position.asset.market.currency
            )
        ).usingRecursiveComparison()
            .isEqualTo(usdValues)
        assertThat(
            position.getMoneyValues(
                Position.In.BASE,
                portfolio.base
            )
        ).usingRecursiveComparison()
            .isEqualTo(usdValues)
    }

    private fun verifyPortfolioMoneyValues(
        pfValues: MoneyValues,
        marketData: MarketData,
        simpleRate: BigDecimal,
        position: Position
    ) {
        pfValues.marketValue = BigDecimal("0")
        pfValues.averageCost = BigDecimal.ZERO
        pfValues.priceData =
            of(
                marketData,
                simpleRate
            )
        pfValues.purchases = fourH
        pfValues.costValue = BigDecimal.ZERO
        pfValues.sales = BigDecimal("600.00")
        pfValues.realisedGain = twoH
        pfValues.unrealisedGain = BigDecimal.ZERO
        pfValues.totalGain = twoH
        assertThat(
            position.getMoneyValues(
                Position.In.PORTFOLIO,
                portfolio.currency
            )
        ).usingRecursiveComparison()
            .isEqualTo(pfValues)
    }

    @Test
    fun `should compute market value with no price for buy behaviour`() {
        val asset =
            getTestAsset(
                NASDAQ,
                "ABC"
            )
        val simpleRate = BigDecimal("0.20")
        val buyTrn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = hundred,
                tradeAmount = BigDecimal("2000.00"),
                portfolio = getPortfolio()
            )
        assertThat(buyTrn.tradeCurrency.code).isEqualTo(asset.market.currency.code)
        buyTrn.tradePortfolioRate = simpleRate
        // Behaviour tests manage their own Positions.
        val positions = Positions(getPortfolio())

        positions.add(
            buyBehaviour.accumulate(
                buyTrn,
                positions
            )
        )
        val marketData = MarketData(asset)
        val fxRateMap =
            getRates(
                buyTrn.portfolio,
                asset,
                simpleRate
            )

        // Revalue based on No Market data
        val result =
            MarketValue(Gains()).value(
                positions,
                marketData,
                fxRateMap
            )
        assertThat(result).isNotNull.hasFieldOrProperty("moneyValues")
    }

    @Test
    fun `should preserve position values when price returns zero`() {
        // Scenario: Position was valued at $100/share, now bc-data returns no price (close=0)
        // The position should still be usable and not crash
        val positions = Positions(portfolio)
        val position =
            buyBehaviour.accumulate(
                Trn(
                    trnType = TrnType.BUY,
                    asset = asset,
                    quantity = hundred,
                    tradeAmount = twoThousand,
                    tradePortfolioRate = simpleRate,
                    portfolio = portfolio
                ),
                positions
            )

        // First valuation with valid price
        val validMarketData =
            MarketData(
                asset,
                close = BigDecimal("20.00"),
                previousClose = BigDecimal("19.00")
            )
        val fxRateMap = getRates(portfolio, asset, simpleRate)

        MarketValue(Gains()).value(positions, validMarketData, fxRateMap)

        val tradeValues = position.getMoneyValues(Position.In.TRADE, asset.market.currency)
        assertThat(tradeValues.marketValue).isEqualTo(twoThousand) // 100 * $20

        // Second valuation with zero/missing price (simulates bc-data rate limit failure)
        val zeroMarketData =
            MarketData(
                asset,
                close = BigDecimal.ZERO // No price available
            )

        MarketValue(Gains()).value(positions, zeroMarketData, fxRateMap)

        // Position should handle zero price gracefully
        val tradeValuesAfterZero = position.getMoneyValues(Position.In.TRADE, asset.market.currency)
        // Market value becomes 0 when price is 0 (quantity * 0 = 0)
        assertThat(tradeValuesAfterZero.marketValue).isEqualTo(BigDecimal.ZERO)
        // But cost basis should be preserved
        assertThat(tradeValuesAfterZero.costBasis).isEqualTo(twoThousand)
        // And quantity should be unchanged
        assertThat(position.quantityValues.getTotal()).isEqualTo(hundred)
    }

    private fun getRates(
        portfolio: Portfolio,
        asset: Asset,
        simpleRate: BigDecimal
    ): Map<IsoCurrencyPair, FxRate> {
        val fxRateMap: MutableMap<IsoCurrencyPair, FxRate> = HashMap()
        val pair =
            toPair(
                asset.market.currency,
                portfolio.currency
            )
        if (pair != null) {
            fxRateMap[pair] =
                FxRate(
                    Currency(Constants.TEST.lowercase()),
                    Currency(Constants.TEST),
                    simpleRate
                )
        }
        return fxRateMap
    }
}