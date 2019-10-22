package com.beancounter.position;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.accumulation.Buy;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.model.FxReport;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestMarketValuesWithFx {
  private MathUtils mathUtils = new MathUtils();

  @Test
  @VisibleForTesting
  void is_MarketValue() {
    Portfolio portfolio = Portfolio.builder()
        .code("MV")
        .currency(getCurrency("NZD"))
        .build();

    Asset asset = AssetUtils.getAsset("ABC", "Test");
    BigDecimal simpleRate = new BigDecimal("0.1");

    Transaction buyTrn = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .portfolio(portfolio)
        .tradeAmount(new BigDecimal("2000.00"))
        .tradePortfolioRate(simpleRate)
        .quantity(new BigDecimal("100")).build();

    Buy buy = new Buy();

    Position position = Position.builder().asset(asset).build();
    buy.value(buyTrn, position);

    MarketData marketData = MarketData.builder()
        .close(new BigDecimal("10.00"))
        .build();

    MarketValue marketValue = new MarketValue();
    FxReport fxReport = FxReport.builder()
        .portfolio(portfolio.getCurrency())
        .base(portfolio.getBase())
        .build();
    Map<CurrencyPair, FxRate> fxRateMap = new HashMap<>();

    fxRateMap.put(
        CurrencyPair.from(
            portfolio.getCurrency(),
            asset.getMarket().getCurrency()
        ),
        FxRate.builder().rate(simpleRate).build());

    // Revalue based on marketData prices
    marketValue.value(position, fxReport, marketData, fxRateMap);

    assertThat(position.getMoneyValue(Position.In.TRADE))
        .isEqualToComparingFieldByField(MoneyValues.builder()
            .price(marketData.getClose())
            .averageCost(new BigDecimal("20.00"))
            .currency(getCurrency("USD"))
            .purchases(buyTrn.getTradeAmount())
            .costBasis(buyTrn.getTradeAmount())
            .costValue(buyTrn.getTradeAmount())
            .totalGain(new BigDecimal("-1000.00"))
            .unrealisedGain(new BigDecimal("-1000.00"))
            .marketValue(mathUtils.multiply(buyTrn.getQuantity(), marketData.getClose()))
            .build());


    assertThat(position.getMoneyValue(Position.In.BASE))
        .isEqualToComparingFieldByField(MoneyValues.builder()
            .price(marketData.getClose())
            .averageCost(new BigDecimal("20.00"))
            .currency(getCurrency("USD"))
            .purchases(buyTrn.getTradeAmount())
            .costBasis(buyTrn.getTradeAmount())
            .costValue(buyTrn.getTradeAmount())
            .totalGain(new BigDecimal("-1000.00"))
            .unrealisedGain(new BigDecimal("-1000.00"))
            .marketValue(mathUtils.multiply(buyTrn.getQuantity(), marketData.getClose()))
            .build());

    // Basically 10% of the non-portfolio values due to the simpleRate value used
    assertThat(position.getMoneyValue(Position.In.PORTFOLIO))
        .isEqualToComparingFieldByField(MoneyValues.builder()
            .price(new BigDecimal("1.00"))
            .currency(portfolio.getCurrency())
            .averageCost(new BigDecimal("2.00"))
            .purchases(new BigDecimal("200.00"))
            .costBasis(new BigDecimal("200.00"))
            .costValue(new BigDecimal("200.00"))
            .totalGain(new BigDecimal("-100.00"))
            .unrealisedGain(new BigDecimal("-100.00"))
            .marketValue(new BigDecimal("100.00"))
            .build());

  }
}
