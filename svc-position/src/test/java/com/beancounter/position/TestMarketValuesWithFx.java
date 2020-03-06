package com.beancounter.position;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.accumulation.BuyBehaviour;
import com.beancounter.position.valuation.Gains;
import com.beancounter.position.valuation.MarketValue;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestMarketValuesWithFx {

  @Test
  void is_MarketValue() {
    Portfolio portfolio = Portfolio.builder()
        .code("MV")
        .currency(getCurrency("NZD"))
        .build();

    Asset asset = AssetUtils.getAsset("ABC", "Test");
    BigDecimal simpleRate = new BigDecimal("0.1");

    Trn buyTrn = Trn.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .tradeAmount(new BigDecimal("2000.00"))
        .tradeCurrency(asset.getMarket().getCurrency())
        .tradePortfolioRate(simpleRate)
        .quantity(new BigDecimal("100")).build();

    BuyBehaviour buyBehaviour = new BuyBehaviour();

    Position position = Position.builder().asset(asset).build();
    buyBehaviour.accumulate(buyTrn, portfolio, position);
    Positions positions = new Positions(portfolio);
    positions.add(position);

    MarketData marketData = MarketData.builder()
        .asset(asset)
        .close(new BigDecimal("10.00"))
        .build();

    Map<IsoCurrencyPair, FxRate> fxRateMap = new HashMap<>();

    fxRateMap.put(
        IsoCurrencyPair.from(
            portfolio.getCurrency(),
            asset.getMarket().getCurrency()
        ),
        FxRate.builder().rate(simpleRate).build());

    // Revalue based on marketData prices
    new MarketValue(new Gains()).value(positions, marketData, fxRateMap);

    assertThat(position.getMoneyValues(Position.In.TRADE))
        .isEqualToComparingFieldByField(MoneyValues.builder()
            .price(marketData.getClose())
            .averageCost(new BigDecimal("20.00"))
            .currency(getCurrency("USD"))
            .purchases(buyTrn.getTradeAmount())
            .costBasis(buyTrn.getTradeAmount())
            .costValue(buyTrn.getTradeAmount())
            .totalGain(new BigDecimal("-1000.00"))
            .unrealisedGain(new BigDecimal("-1000.00"))
            .marketValue(MathUtils.multiply(buyTrn.getQuantity(), marketData.getClose()))
            .build());


    assertThat(position.getMoneyValues(Position.In.BASE))
        .isEqualToComparingFieldByField(MoneyValues.builder()
            .price(marketData.getClose())
            .averageCost(new BigDecimal("20.00"))
            .currency(getCurrency("USD"))
            .purchases(buyTrn.getTradeAmount())
            .costBasis(buyTrn.getTradeAmount())
            .costValue(buyTrn.getTradeAmount())
            .totalGain(new BigDecimal("-1000.00"))
            .unrealisedGain(new BigDecimal("-1000.00"))
            .marketValue(MathUtils.multiply(buyTrn.getQuantity(), marketData.getClose()))
            .build());

    // Basically 10% of the non-portfolio values due to the simpleRate value used
    assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
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
