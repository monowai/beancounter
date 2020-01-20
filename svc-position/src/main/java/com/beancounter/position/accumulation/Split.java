package com.beancounter.position.accumulation;

import static com.beancounter.position.utils.PositionUtils.getCurrency;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Trn;
import java.math.BigDecimal;

public class Split implements ValueTransaction {

  public void value(Trn trn, Portfolio portfolio, Position position) {
    BigDecimal total = position.getQuantityValues().getTotal();
    position.getQuantityValues()
        .setAdjustment((trn.getQuantity().multiply(total)).subtract(total));

    value(position, position.getMoneyValues(Position.In.TRADE,
        getCurrency(Position.In.TRADE, portfolio, trn)));
    value(position, position.getMoneyValues(Position.In.BASE,
        getCurrency(Position.In.BASE, portfolio, trn)));
    value(position, position.getMoneyValues(Position.In.PORTFOLIO,
        getCurrency(Position.In.PORTFOLIO, portfolio, trn)));
  }

  private void value(Position position, MoneyValues moneyValues) {
    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {
      moneyValues.setAverageCost(
          Cost.average(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );
    }

    Cost.setCostValue(position, moneyValues);
  }
}
