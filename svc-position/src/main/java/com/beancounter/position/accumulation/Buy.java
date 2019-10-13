package com.beancounter.position.accumulation;

import com.beancounter.common.helper.MathHelper;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;

public class Buy implements AccumulationLogic {
  private Cost cost = new Cost();
  private MathHelper mathHelper = new MathHelper();

  public void value(Transaction transaction, Position position) {
    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setPurchased(quantityValues.getPurchased().add(transaction.getQuantity()));

    value(transaction, position, Position.In.TRADE, BigDecimal.ONE);
    value(transaction, position, Position.In.BASE, transaction.getTradeBaseRate());
    value(transaction, position, Position.In.CASH, transaction.getTradeCashRate());
    value(transaction, position, Position.In.PORTFOLIO, transaction.getTradePortfolioRate());

  }

  private void value(Transaction transaction,
                     Position position,
                     Position.In in,
                     BigDecimal rate) {

    MoneyValues moneyValues = position.getMoneyValue(in);

    moneyValues.setPurchases(moneyValues.getPurchases().add(
        mathHelper.multiply(transaction.getTradeAmount(), rate))
    );

    moneyValues.setCostBasis(moneyValues.getCostBasis().add(
        mathHelper.multiply(transaction.getTradeAmount(), rate))
    );

    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {

      moneyValues.setAverageCost(
          cost.average(moneyValues.getCostBasis(), position.getQuantityValues().getTotal())
      );

    }
    cost.setCostValue(position, moneyValues);
  }
}
