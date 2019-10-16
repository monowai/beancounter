package com.beancounter.position.accumulation;

import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;

public class MarketValue {
  private Gains gains = new Gains();
  private MathUtils mathUtils = new MathUtils();

  public void value(Position position, Position.In in, MarketData marketData, BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValue(in);
    BigDecimal total = position.getQuantityValues().getTotal();
    moneyValues.setAsAt(marketData.getDate());
    moneyValues.setPrice(mathUtils.multiply(marketData.getClose(), rate));
    moneyValues.setMarketValue(moneyValues.getPrice().multiply(total));

    gains.value(position, in);
  }

}
