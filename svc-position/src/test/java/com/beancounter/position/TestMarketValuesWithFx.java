package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.accumulation.Buy;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.model.Position;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TestMarketValuesWithFx {
  @Test
  @VisibleForTesting
  void is_MarketValue() {
    Transaction buyTrn = Transaction.builder()
        .trnType(TrnType.BUY)
        .tradeAmount(new BigDecimal("2000.00"))
        .quantity(new BigDecimal("100")).build();

    Buy buy = new Buy();
    MathUtils mathUtils = new MathUtils();
    Asset asset = AssetUtils.getAsset("ABC", "Test");
    Position position = Position.builder().asset(asset).build();
    buy.value(buyTrn, position);

    MarketData marketData = MarketData.builder().close(new BigDecimal("10.00")).build();
    MoneyValues targetPortfolioValues = MoneyValues.builder()
        .price(marketData.getClose())
        .averageCost(new BigDecimal("20.00"))
        .purchases(buyTrn.getTradeAmount())
        .costBasis(buyTrn.getTradeAmount())
        .costValue(buyTrn.getTradeAmount())
        .totalGain(new BigDecimal("-1000.00"))
        .unrealisedGain(new BigDecimal("-1000.00"))
        .marketValue(mathUtils.multiply(buyTrn.getQuantity(), BigDecimal.TEN))
        .build();

    MarketValue marketValue = new MarketValue();
    marketValue.value(position, Position.In.TRADE, marketData, BigDecimal.ONE);
    marketValue.value(position, Position.In.BASE, marketData, BigDecimal.ONE);

    assertThat(position.getMoneyValue(Position.In.TRADE))
        .isEqualToComparingFieldByField(targetPortfolioValues);
    assertThat(position.getMoneyValue(Position.In.BASE))
        .isEqualToComparingFieldByField(targetPortfolioValues);

  }
}
