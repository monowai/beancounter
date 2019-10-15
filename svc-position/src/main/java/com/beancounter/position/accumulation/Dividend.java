package com.beancounter.position.accumulation;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;

public class Dividend implements AccumulationLogic {

  private MathUtils mathUtils = new MathUtils();

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
        mathUtils.add(moneyValues.getDividends(),
            mathUtils.multiply(transaction.getTradeAmount(), rate)));

  }

}
