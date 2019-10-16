package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;

public class Split implements ValueTransaction {
  private Cost cost = new Cost();

  public void value(Transaction transaction, Position position) {
    BigDecimal total = position.getQuantityValues().getTotal();
    position.getQuantityValues()
        .setAdjustment((transaction.getQuantity().multiply(total)).subtract(total));

    value(position, position.getMoneyValue(Position.In.TRADE));
    value(position, position.getMoneyValue(Position.In.BASE));
    value(position, position.getMoneyValue(Position.In.CASH));
    value(position, position.getMoneyValue(Position.In.PORTFOLIO));
  }

  private void value(Position position, MoneyValues moneyValues) {
    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {
      moneyValues.setAverageCost(
          cost.average(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );
    }

    cost.setCostValue(position, moneyValues);
  }
}
