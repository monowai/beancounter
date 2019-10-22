package com.beancounter.position.accumulation;

import static com.beancounter.position.utils.PositionUtils.getCurrency;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.MathUtils;
import java.math.BigDecimal;

public class Buy implements ValueTransaction {
  private Cost cost = new Cost();
  private MathUtils mathUtils = new MathUtils();

  public void value(Transaction transaction, Position position) {
    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setPurchased(quantityValues.getPurchased().add(transaction.getQuantity()));

    value(transaction, position, Position.In.TRADE, BigDecimal.ONE);
    value(transaction, position, Position.In.BASE, transaction.getTradeBaseRate());
    value(transaction, position, Position.In.PORTFOLIO, transaction.getTradePortfolioRate());

  }

  private void value(Transaction transaction,
                     Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValue(in, getCurrency(in, transaction));

    moneyValues.setPurchases(moneyValues.getPurchases().add(
        mathUtils.multiply(transaction.getTradeAmount(), rate))
    );

    moneyValues.setCostBasis(moneyValues.getCostBasis().add(
        mathUtils.multiply(transaction.getTradeAmount(), rate))
    );

    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {

      moneyValues.setAverageCost(
          cost.average(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );

    }
    cost.setCostValue(position, moneyValues);
  }
}
