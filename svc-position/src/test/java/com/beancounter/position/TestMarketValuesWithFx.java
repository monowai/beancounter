package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.PriceData;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.position.accumulation.AccumulationStrategy;
import com.beancounter.position.accumulation.BuyBehaviour;
import com.beancounter.position.accumulation.SellBehaviour;
import com.beancounter.position.valuation.Gains;
import com.beancounter.position.valuation.MarketValue;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class TestMarketValuesWithFx {

  @Test
  void is_MarketValue() {
    Asset asset = AssetUtils.getAsset("Test", "ABC");
    BigDecimal simpleRate = new BigDecimal("0.20");

    Trn buyTrn = new Trn(TrnType.BUY, asset);
    buyTrn.setTradeAmount(new BigDecimal("2000.00"));
    buyTrn.setTradeCurrency(asset.getMarket().getCurrency());
    buyTrn.setTradePortfolioRate(simpleRate);
    buyTrn.setQuantity(new BigDecimal("100"));

    AccumulationStrategy buyBehaviour = new BuyBehaviour();

    Position position = new Position(asset);
    Portfolio portfolio = PortfolioUtils.getPortfolio("MV");
    buyBehaviour.accumulate(buyTrn, portfolio, position);
    Positions positions = new Positions(portfolio);
    positions.add(position);

    MarketData marketData = new MarketData(asset);
    marketData.setClose(new BigDecimal("10.00"));
    marketData.setPreviousClose(new BigDecimal("5.00"));


    // Revalue based on marketData prices
    MoneyValues targetValues = new MoneyValues(new Currency("USD"));
    targetValues.setPriceData(PriceData.Companion.of(marketData));
    targetValues.setAverageCost(new BigDecimal("20.00"));
    targetValues.setPurchases(buyTrn.getTradeAmount());
    targetValues.setCostBasis(buyTrn.getTradeAmount());
    targetValues.setCostValue(buyTrn.getTradeAmount());
    targetValues.setTotalGain(new BigDecimal("-1000.00"));
    targetValues.setUnrealisedGain(new BigDecimal("-1000.00"));
    targetValues.setMarketValue(Objects.requireNonNull(
        MathUtils.multiply(buyTrn.getQuantity(), marketData.getClose())));

    Map<IsoCurrencyPair, FxRate> fxRateMap = getRates(portfolio, asset, simpleRate);
    new MarketValue(new Gains()).value(positions, marketData, fxRateMap);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .isEqualToIgnoringGivenFields(targetValues, "priceData", "portfolio");

    MoneyValues baseValues = new MoneyValues(new Currency("USD"));
    baseValues.setAverageCost(new BigDecimal("20.00"));
    baseValues.setPriceData(PriceData.Companion.of(marketData));
    baseValues.setPurchases(buyTrn.getTradeAmount());
    baseValues.setCostBasis(buyTrn.getTradeAmount());
    baseValues.setCostValue(buyTrn.getTradeAmount());
    baseValues.setTotalGain(new BigDecimal("-1000.00"));
    baseValues.setUnrealisedGain(new BigDecimal("-1000.00"));
    baseValues.setMarketValue(
        MathUtils.nullSafe(MathUtils.multiply(buyTrn.getQuantity(), marketData.getClose())));

    assertThat(position.getMoneyValues(Position.In.BASE))
        .isEqualToIgnoringGivenFields(baseValues, "priceData", "portfolio");
    MoneyValues pfValues = new MoneyValues(portfolio.getCurrency());
    pfValues.setCostBasis(new BigDecimal("10000.00"));
    pfValues.setPurchases(new BigDecimal("10000.00"));
    pfValues.setPriceData(PriceData.Companion.of(marketData, simpleRate));
    pfValues.setMarketValue(new BigDecimal("200.00"));
    pfValues.setAverageCost(new BigDecimal("100.00"));
    pfValues.setCostValue(new BigDecimal("10000.00"));
    pfValues.setUnrealisedGain(new BigDecimal("-9800.00"));
    pfValues.setTotalGain(new BigDecimal("-9800.00"));

    assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
        .isEqualToIgnoringGivenFields(pfValues, "priceData");

  }

  @Test
  void is_GainsOnSell() {
    Portfolio portfolio = PortfolioUtils.getPortfolio("MV");

    Asset asset = AssetUtils.getAsset("Test", "ABC");
    Map<IsoCurrencyPair, FxRate> fxRateMap = new HashMap<>();

    BigDecimal simpleRate = new BigDecimal("0.20");

    fxRateMap.put(
        IsoCurrencyPair.toPair(
            portfolio.getCurrency(),
            asset.getMarket().getCurrency()),
        new FxRate(
            new Currency("X"), new Currency("X"),
            simpleRate, null));

    Trn buyTrn = new Trn(TrnType.BUY, asset);
    buyTrn.setTradeAmount(new BigDecimal("2000.00"));
    buyTrn.setTradeCurrency(asset.getMarket().getCurrency());
    buyTrn.setTradePortfolioRate(simpleRate);
    buyTrn.setQuantity(new BigDecimal("100"));

    AccumulationStrategy buyBehaviour = new BuyBehaviour();

    Position position = new Position(asset);
    buyBehaviour.accumulate(buyTrn, portfolio, position);
    Positions positions = new Positions(portfolio);
    positions.add(position);

    Trn sellTrn = new Trn(TrnType.SELL, asset);
    sellTrn.setTradeAmount(new BigDecimal("3000.00"));
    sellTrn.setTradeCurrency(asset.getMarket().getCurrency());
    sellTrn.setTradePortfolioRate(simpleRate);
    sellTrn.setQuantity(new BigDecimal("100"));

    AccumulationStrategy sellBehaviour = new SellBehaviour();
    sellBehaviour.accumulate(sellTrn, portfolio, position);
    MarketData marketData = new MarketData(asset);
    marketData.setClose(new BigDecimal("10.00"));
    marketData.setPreviousClose(new BigDecimal("9.00"));

    new MarketValue(new Gains()).value(positions, marketData, fxRateMap);

    MoneyValues usdValues = new MoneyValues(new Currency("USD"));
    usdValues.setMarketValue(new BigDecimal("0"));
    usdValues.setAverageCost(BigDecimal.ZERO);
    usdValues.setPriceData(PriceData.Companion.of(marketData, BigDecimal.ONE));
    usdValues.setPurchases(buyTrn.getTradeAmount());
    usdValues.setSales(sellTrn.getTradeAmount());
    usdValues.setCostValue(BigDecimal.ZERO);
    usdValues.setRealisedGain(new BigDecimal("1000.00"));
    usdValues.setUnrealisedGain(BigDecimal.ZERO);
    usdValues.setTotalGain(new BigDecimal("1000.00"));

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .isEqualToIgnoringGivenFields(usdValues, "priceData", "portfolio");

    assertThat(position.getMoneyValues(Position.In.BASE))
        .isEqualToIgnoringGivenFields(usdValues, "priceData", "portfolio");

    MoneyValues pfValues = new MoneyValues(portfolio.getCurrency());
    pfValues.setMarketValue(new BigDecimal("0"));
    pfValues.setAverageCost(BigDecimal.ZERO);
    pfValues.setPriceData(PriceData.Companion.of(marketData, simpleRate));
    pfValues.setPurchases(
        Objects.requireNonNull(MathUtils.divide(buyTrn.getTradeAmount(), simpleRate)));
    pfValues.setCostValue(BigDecimal.ZERO);
    pfValues.setSales(MathUtils.divide(sellTrn.getTradeAmount(), simpleRate));
    pfValues.setRealisedGain(new BigDecimal("5000.00"));
    pfValues.setUnrealisedGain(BigDecimal.ZERO);
    pfValues.setTotalGain(new BigDecimal("5000.00"));

    assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
        .isEqualToIgnoringGivenFields(pfValues, "priceData", "portfolio");
  }

  @Test
  void is_MarketValueWithNoPriceComputed() {

    Asset asset = AssetUtils.getAsset("Test", "ABC");
    BigDecimal simpleRate = new BigDecimal("0.20");

    Trn buyTrn = new Trn(TrnType.BUY, asset);
    buyTrn.setTradeAmount(new BigDecimal("2000.00"));
    buyTrn.setTradeCurrency(asset.getMarket().getCurrency());
    buyTrn.setTradePortfolioRate(simpleRate);
    buyTrn.setQuantity(new BigDecimal("100"));

    AccumulationStrategy buyBehaviour = new BuyBehaviour();

    Position position = new Position(asset);
    Portfolio portfolio = PortfolioUtils.getPortfolio("MV");
    buyBehaviour.accumulate(buyTrn, portfolio, position);
    Positions positions = new Positions(portfolio);
    positions.add(position);

    MarketData marketData = new MarketData(asset);

    Map<IsoCurrencyPair, FxRate> fxRateMap = getRates(portfolio, asset, simpleRate);

    // Revalue based on No Market data
    Position result = new MarketValue(new Gains()).value(positions, marketData, fxRateMap);
    assertThat(result).isNotNull().hasFieldOrProperty("moneyValues");

  }

  private Map<IsoCurrencyPair, FxRate> getRates(
      Portfolio portfolio,
      Asset asset,
      BigDecimal simpleRate) {
    Map<IsoCurrencyPair, FxRate> fxRateMap = new HashMap<>();

    fxRateMap.put(
        IsoCurrencyPair.toPair(
            portfolio.getCurrency(),
            asset.getMarket().getCurrency()
        ),
        new FxRate(new Currency("test"), new Currency("TEST"),
            simpleRate, null));
    return fxRateMap;
  }
}
