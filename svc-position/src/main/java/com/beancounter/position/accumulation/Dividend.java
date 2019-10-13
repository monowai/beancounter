package com.beancounter.position.accumulation;

import com.beancounter.common.helper.MathHelper;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;

public class Dividend implements AccumulationLogic {

  private MathHelper mathHelper = new MathHelper();

  public void value(Transaction transaction, Position position) {
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
    moneyValues.setDividends(
        mathHelper.add(moneyValues.getDividends(),
            mathHelper.multiply(transaction.getTradeAmount(), rate)));

  }

}
