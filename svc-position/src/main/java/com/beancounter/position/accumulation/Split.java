package com.beancounter.position.accumulation;

import static com.beancounter.position.utils.PositionalCurrency.getCurrency;

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

    value(position, position.getMoneyValue(Position.In.TRADE,
        getCurrency(Position.In.TRADE, transaction)));
    value(position, position.getMoneyValue(Position.In.BASE,
        getCurrency(Position.In.BASE, transaction)));
    value(position, position.getMoneyValue(Position.In.PORTFOLIO,
        getCurrency(Position.In.PORTFOLIO, transaction)));
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
